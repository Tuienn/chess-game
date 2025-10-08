import os
import asyncio
import logging
import time
from typing import Dict, List, Optional, TypedDict, Literal

import socketio
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# --- Optional chess/engine (for PVP validation and AI) ---
import chess
import chess.engine

from dotenv import load_dotenv
load_dotenv()

# ----------------------------
# Logging
# ----------------------------
logging.basicConfig(level=logging.INFO)
log = logging.getLogger("chess-server")

# ----------------------------
# Kiểu dữ liệu
# ----------------------------
Color = Literal["WHITE", "BLACK"]

class ChessMove(TypedDict, total=False):
    from_: int  # dùng from_ vì 'from' là keyword của Python
    to: int
    promo: Optional[Literal["Q", "R", "B", "N"]]
    isCastle: bool
    isEnPassant: bool
    isDoublePawnPush: bool

class GameState(TypedDict):
    lastMove: Optional[ChessMove]
    boardFEN: str

class PlayerInfo(TypedDict):
    uid: str
    color: Color
    socketId: str

class RoomState(TypedDict):
    code: str
    players: List[PlayerInfo]
    state: GameState
    sideToMove: Color
    lastActive: float  # milliseconds epoch
    timeControlMs: Optional[int]  # Time control in milliseconds, None = no limit
    whiteTimeMs: Optional[float]  # White's remaining time
    blackTimeMs: Optional[float]  # Black's remaining time
    turnStartTime: Optional[float]  # When current turn started (ms epoch)

# ----------------------------
# Server state (in-memory)
# ----------------------------
rooms: Dict[str, RoomState] = {}
memberships: Dict[str, Dict[str, str]] = {}
boards: Dict[str, chess.Board] = {}

# Engine Stockfish (global) + lock
ENGINE_SKILL = int(os.environ.get("STOCKFISH_SKILL", "16"))
STOCKFISH_PATH = os.environ.get("STOCKFISH_PATH", r"C:\Users\NC\Downloads\stockfish-windows-x86-64-avx2\stockfish\stockfish-windows-x86-64-avx2.exe")
ENGINE_LOCK = asyncio.Lock()
engine: Optional[chess.engine.SimpleEngine] = None

# ----------------------------
# FastAPI + Socket.IO (ASGI)
# ----------------------------
app = FastAPI(title="Chess Server (Python)")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

sio = socketio.AsyncServer(
    async_mode="asgi",
    cors_allowed_origins="*",
    logger=False,
    engineio_logger=False,
)
asgi_app = socketio.ASGIApp(sio, other_asgi_app=app)

PORT = int(os.environ.get("PORT", "4001"))

# ----------------------------
# Helpers
# ----------------------------
def now_ms() -> float:
    return time.time() * 1000.0

def gen_code() -> str:
    """Generate a six character uppercase room code."""
    import secrets, string as _s
    return "".join(secrets.choice(_s.ascii_uppercase) for _ in range(6))

def initial_state() -> GameState:
    return {"lastMove": None, "boardFEN": "startpos"}

def create_room(code: str, time_control_ms: Optional[int] = None) -> RoomState:
    room: RoomState = {
        "code": code,
        "players": [],
        "state": initial_state(),
        "sideToMove": "WHITE",
        "lastActive": now_ms(),
        "timeControlMs": time_control_ms,
        "whiteTimeMs": float(time_control_ms) if time_control_ms else None,
        "blackTimeMs": float(time_control_ms) if time_control_ms else None,
        "turnStartTime": None  # Will be set when game starts
    }
    boards[code] = chess.Board()
    return room

PROMO_MAP = {"Q": chess.QUEEN, "R": chess.ROOK, "B": chess.BISHOP, "N": chess.KNIGHT}
REV_PROMO_MAP = {v: k for k, v in PROMO_MAP.items()}

def move_dict_from_chess_move(m: chess.Move, board: chess.Board, board_before_move: chess.Board = None) -> ChessMove:
    md: ChessMove = {
        "from_": m.from_square,
        "to": m.to_square,
        "promo": None,
        "isCastle": False,
        "isEnPassant": False,
        "isDoublePawnPush": False,
    }
    if m.promotion:
        md["promo"] = REV_PROMO_MAP.get(m.promotion)
    
    # Use board_before_move to check piece type if available
    check_board = board_before_move if board_before_move else board
    
    # Castle detection: king moves two files
    piece = check_board.piece_at(m.from_square)
    if piece and piece.piece_type == chess.KING:
        if abs(chess.square_file(m.from_square) - chess.square_file(m.to_square)) == 2:
            md["isCastle"] = True
    
    # En passant detection
    if piece and piece.piece_type == chess.PAWN:
        # Check if it's an en passant capture
        if m.to_square == check_board.ep_square:
            md["isEnPassant"] = True
        # Check if it's a double pawn push
        rank_diff = abs(chess.square_rank(m.from_square) - chess.square_rank(m.to_square))
        if rank_diff == 2:
            md["isDoublePawnPush"] = True
    
    return md

def apply_player_move_to_board(board: chess.Board, mv: dict) -> chess.Move:
    src = mv.get("from_") if "from_" in mv else mv.get("from")
    dst = mv.get("to")
    promo = mv.get("promo")
    if not isinstance(src, int) or not isinstance(dst, int):
        raise ValueError("Move 'from'/'to' must be integers [0..63]")

    promotion = None
    if promo:
        if promo not in PROMO_MAP:
            raise ValueError("Invalid promotion piece (use one of Q,R,B,N)")
        promotion = PROMO_MAP[promo]

    m = chess.Move(src, dst, promotion=promotion)
    if m not in board.legal_moves:
        raise ValueError("Illegal move")
    board.push(m)
    return m

async def emit_room_state(room: RoomState):
    code = room["code"]
    board = boards.get(code)
    # Luôn cập nhật FEN thực từ board (nếu có)
    if board:
        room["state"]["boardFEN"] = board.fen()
    payload = {
        "code": code,
        "players": [{"uid": p["uid"], "color": p["color"]} for p in room["players"]],
        "sideToMove": room["sideToMove"],
        "state": room["state"],
    }
    await sio.emit("room_state", payload, room=code)

async def remove_membership_and_update_room(sid: str):
    membership = memberships.get(sid)
    if not membership:
        return

    code, uid = membership["code"], membership["uid"]
    room = rooms.get(code)
    if not room:
        memberships.pop(sid, None)
        return

    # Chỉ xóa player nếu sid rời trùng với socket hiện tại của player (tránh reconnect)
    idx = next((i for i, p in enumerate(room["players"]) if p["uid"] == uid), -1)
    if idx != -1:
        player = room["players"][idx]
        if player["socketId"] != sid:
            memberships.pop(sid, None)
            return
        room["players"].pop(idx)
        room["lastActive"] = now_ms()
        await emit_room_state(room)

    if len(room["players"]) == 0:
        # Reset room & board khi trống
        room["sideToMove"] = "WHITE"
        room["state"] = initial_state()
        boards[code] = chess.Board()

    memberships.pop(sid, None)

# ----------------------------
# REST: POST /room
# ----------------------------
from fastapi import HTTPException

@app.post("/room")
async def create_room_endpoint():
    # tạo mã code duy nhất
    tries = 0
    code = gen_code()
    while code in rooms:
        code = gen_code()
        tries += 1
        if tries > 100:
            raise HTTPException(500, "Cannot allocate room code")
    rooms[code] = create_room(code)
    log.info(f"[DEBUG] Room created: {code} (total={len(rooms)})")
    return {"code": code}

# ----------------------------
# Socket.IO events
# ----------------------------
@sio.event
async def connect(sid, environ, auth):
    log.info(f"[DEBUG] Client connected: {sid}")

@sio.event
async def join_room(sid, payload):
    try:
        if not isinstance(payload, dict):
            raise ValueError("Payload must be an object")

        code = payload.get("code")
        uid = payload.get("uid")

        if not code or not isinstance(code, str):
            raise ValueError("Room code is required")
        if not uid or not isinstance(uid, str):
            raise ValueError("User id is required")

        room = rooms.get(code)
        if not room:
            raise ValueError("Room not found")

        # Nếu sid đang ở phòng khác, rời phòng cũ & cập nhật
        prev = memberships.get(sid)
        if prev and prev.get("code") != code:
            await sio.leave_room(sid, prev["code"])
            await remove_membership_and_update_room(sid)

        # Tìm player cùng uid (reconnect) hoặc thêm mới
        existing = next((p for p in room["players"] if p["uid"] == uid), None)
        if not existing and len(room["players"]) >= 2:
            raise ValueError("Room is full")

        if existing:
            existing["socketId"] = sid
            player = existing
            log.info(f"[DEBUG] Player {uid} reconnected to {code} as {player['color']}")
        else:
            color: Color = "WHITE" if len(room["players"]) == 0 else "BLACK"
            player = {"uid": uid, "color": color, "socketId": sid}
            room["players"].append(player)
            
            # Check for AI game indicators
            is_ai_game = uid.startswith("ai_") or len(room["players"]) == 1
            game_type = "AI_GAME" if is_ai_game else "PVP_GAME"
            log.info(f"[{game_type}] Player {uid} joined {code} as {color}")
            
            if is_ai_game:
                log.info(f"[AI_GAME] Room {code} set up for human vs AI gameplay")

        memberships[sid] = {"code": code, "uid": uid}
        await sio.enter_room(sid, code)
        room["lastActive"] = now_ms()

        # Gửi room_joined cho người mới vào
        await sio.emit("room_joined", {"code": room["code"], "color": player["color"].lower()}, to=sid)

        # Nếu là người thứ 2 (và không phải reconnect), thông báo cho người còn lại
        if not existing and len(room["players"]) == 2:
            other = next((p for p in room["players"] if p["uid"] != uid), None)
            if other:
                await sio.emit("opponent_joined", {}, to=other["socketId"])
            
            # Start timer when both players joined
            if room["timeControlMs"] is not None:
                room["turnStartTime"] = now_ms()
                log.info(f"[TIMER] Game {code} started with time control {room['timeControlMs']}ms")

        # Đồng bộ state hiện tại
        board = boards.get(code)
        if board:
            room["state"]["boardFEN"] = board.fen()
            room["sideToMove"] = "WHITE" if board.turn == chess.WHITE else "BLACK"

        await emit_room_state(room)

    except Exception as e:
        log.info(f"[DEBUG] join_room error for {sid}: {e}")
        await sio.emit("error_msg", {"message": str(e) or "Failed to join room"}, to=sid)

@sio.event
async def move(sid, payload):
    """PVP move: enforce turn and legality using python-chess."""
    try:
        membership = memberships.get(sid)
        if not membership:
            raise ValueError("You are not joined to a room")

        if not isinstance(payload, dict):
            raise ValueError("Payload must be an object")

        code: str = payload.get("code")
        mv = payload.get("move")

        if not code or not isinstance(code, str):
            raise ValueError("Room code is required")
        if not isinstance(mv, dict):
            raise ValueError("Move payload is required")

        room = rooms.get(code)
        board = boards.get(code)
        if not room or board is None:
            raise ValueError("Room not found")

        player = next((p for p in room["players"] if p["uid"] == membership["uid"]), None)
        if not player:
            raise ValueError("Player not found in room")

        # Kiểm tra lượt
        is_whites_turn = board.turn == chess.WHITE
        if (player["color"] == "WHITE" and not is_whites_turn) or (player["color"] == "BLACK" and is_whites_turn):
            raise ValueError("Not your turn")

        # Timer logic - check if time has expired
        if room["timeControlMs"] is not None and room["turnStartTime"] is not None:
            current_time = now_ms()
            elapsed_ms = current_time - room["turnStartTime"]
            
            # Deduct time from active player
            if is_whites_turn:
                if room["whiteTimeMs"] is not None:
                    room["whiteTimeMs"] = max(0, room["whiteTimeMs"] - elapsed_ms)
                    if room["whiteTimeMs"] <= 0:
                        raise ValueError("Time expired - Black wins!")
            else:
                if room["blackTimeMs"] is not None:
                    room["blackTimeMs"] = max(0, room["blackTimeMs"] - elapsed_ms)
                    if room["blackTimeMs"] <= 0:
                        raise ValueError("Time expired - White wins!")

        # Áp dụng nước đi (hợp lệ)
        if "from" in mv and "from_" not in mv:
            mv["from_"] = mv["from"]
        
        # Save board state before move for accurate flag detection
        board_before = board.copy()
        m = apply_player_move_to_board(board, mv)

        # Cập nhật state
        room["state"]["lastMove"] = move_dict_from_chess_move(m, board, board_before)
        room["state"]["boardFEN"] = board.fen()
        room["sideToMove"] = "WHITE" if board.turn == chess.WHITE else "BLACK"
        room["lastActive"] = now_ms()
        
        # Update turn start time for timer
        if room["timeControlMs"] is not None:
            room["turnStartTime"] = now_ms()

        response = {
            "move": room["state"]["lastMove"],
            "sideToMove": room["sideToMove"],
            "state": room["state"],
            "whiteTimeMs": room.get("whiteTimeMs"),
            "blackTimeMs": room.get("blackTimeMs")
        }
        log.info(f"[PVP_GAME] Sending move_applied: {room['state']['lastMove']}")
        await sio.emit("move_applied", response, room=code)

    except Exception as e:
        log.info(f"[DEBUG] move error for {sid}: {e}")
        await sio.emit("error_msg", {"message": str(e) or "Failed to apply move"}, to=sid)

@sio.event
async def move_vs_ai(sid, payload):
    """
    Client -> Server:
      { code: string, move: { from: number, to: number, promo?: "Q"|"R"|"B"|"N" }, level?: number, thinkTimeMs?: number }
    Server -> Client ("ai_move"): see response below.
    """
    try:
        if not isinstance(payload, dict):
            raise ValueError("Payload must be an object")

        code = payload.get("code")
        mv = payload.get("move")
        level = int(payload.get("level", ENGINE_SKILL))
        think_ms = int(payload.get("thinkTimeMs", 300))

        log.info(f"[AI_GAME] Player {sid} in room {code}: AI level={level}, think_time={think_ms}ms")

        if not code or not isinstance(code, str):
            raise ValueError("Room code is required")

        if mv is not None and not isinstance(mv, dict):
            raise ValueError("Move payload must be an object when provided")

        membership = memberships.get(sid)
        if not membership or membership.get("code") != code:
            raise ValueError("You are not joined to this room")

        room = rooms.get(code)
        board = boards.get(code)
        if not room or board is None:
            raise ValueError("Room not found")

        your_move = None
        board_before_your_move = None
        if mv is not None:
            if "from" in mv and "from_" not in mv:
                mv["from_"] = mv["from"]
            
            # Save board state before move
            board_before_your_move = board.copy()
            your_move = apply_player_move_to_board(board, mv)
            
            # Log player move
            from_square = chess.square_name(your_move.from_square)
            to_square = chess.square_name(your_move.to_square)
            promo_str = f" promo={your_move.promotion}" if your_move.promotion else ""
            log.info(f"[AI_GAME] Player move in {code}: {from_square}-{to_square}{promo_str}")

            room["state"]["boardFEN"] = board.fen()
            room["state"]["lastMove"] = move_dict_from_chess_move(your_move, board, board_before_your_move)
            room["sideToMove"] = "WHITE" if board.turn == chess.WHITE else "BLACK"
            room["lastActive"] = now_ms()
        else:
            log.info(f"[AI_GAME] No player move provided in {code} - AI will make opening move")

        if board.is_game_over():
            result = board.result(claim_draw=True)
            log.info(f"[AI_GAME] Game over in {code}: result={result}")
            await sio.emit("ai_move", {
                "code": code,
                "yourMove": room["state"].get("lastMove"),
                "aiMove": None,
                "sideToMove": room["sideToMove"],
                "state": room["state"],
                "gameOver": {"result": result}
            }, room=code)
            return

        # 2) Tính nước của Stockfish
        global engine
        if engine is None:
            raise RuntimeError("Stockfish engine is not running. Set STOCKFISH_PATH correctly.")

        log.info(f"[AI_GAME] Computing AI move for {code} at skill level {level}")
        start_time = time.time()

        async with ENGINE_LOCK:
            try:
                try:
                    engine.configure({"Skill Level": level})
                except Exception:
                    pass
                loop = asyncio.get_running_loop()
                res = await loop.run_in_executor(
                    None,
                    lambda: engine.play(board, chess.engine.Limit(time=max(0.05, think_ms/1000.0)))
                )
            finally:
                try:
                    engine.configure({"Skill Level": ENGINE_SKILL})
                except Exception:
                    pass

        compute_time = (time.time() - start_time) * 1000
        log.info(f"[AI_GAME] AI computation took {compute_time:.1f}ms for {code}")

        # 3) Đẩy nước AI vào bàn cờ
        if res.move is not None:
            # Save board state before AI move
            board_before_ai_move = board.copy()
            board.push(res.move)
            ai_move_dict = move_dict_from_chess_move(res.move, board, board_before_ai_move)
            
            # Log AI move
            from_square = chess.square_name(res.move.from_square)
            to_square = chess.square_name(res.move.to_square)
            promo_str = f" promo={res.move.promotion}" if res.move.promotion else ""
            log.info(f"[AI_GAME] AI move in {code}: {from_square}-{to_square}{promo_str}")
        else:
            ai_move_dict = None
            log.warning(f"[AI_GAME] Stockfish returned no move for {code}")

        room["state"]["boardFEN"] = board.fen()
        if ai_move_dict is not None:
            room["state"]["lastMove"] = ai_move_dict
        elif your_move is not None and board_before_your_move is not None:
            room["state"]["lastMove"] = move_dict_from_chess_move(your_move, board, board_before_your_move)
        room["sideToMove"] = "WHITE" if board.turn == chess.WHITE else "BLACK"
        room["lastActive"] = now_ms()

        game_over = None
        if board.is_game_over():
            game_over = {"result": board.result(claim_draw=True)}
            log.info(f"[AI_GAME] Game ended in {code}: result={game_over['result']}")

        log.info(f"[AI_GAME] Sending ai_move response for {code}: next_turn={room['sideToMove']}")
        await sio.emit("ai_move", {
            "code": code,
            "yourMove": move_dict_from_chess_move(your_move, board, board_before_your_move) if your_move is not None and board_before_your_move is not None else None,
            "aiMove": ai_move_dict,
            "sideToMove": room["sideToMove"],
            "state": room["state"],
            "gameOver": game_over
        }, room=code)

    except Exception as e:
        log.error(f"[AI_GAME] Error in move_vs_ai for {sid} in room {code}: {e}")
        await sio.emit("error_msg", {"message": str(e) or "Failed to play vs AI"}, to=sid)

@sio.event
async def disconnect(sid):
    log.info(f"[DEBUG] Client disconnected: {sid}")
    await remove_membership_and_update_room(sid)

# ----------------------------
# Background cleanup task
# ----------------------------
TEN_MINUTES = 10 * 60 * 1000

async def cleanup_stale_rooms():
    while True:
        try:
            current = now_ms()
            stale: List[str] = []
            for code, room in list(rooms.items()):
                if len(room["players"]) == 0 and (current - room["lastActive"] > TEN_MINUTES):
                    stale.append(code)
            for code in stale:
                rooms.pop(code, None)
                boards.pop(code, None)
                log.info(f"[DEBUG] Cleaned stale room: {code}")
        except Exception as e:
            log.warning(f"[DEBUG] Cleanup error: {e}")
        await asyncio.sleep(60)

@app.on_event("startup")
async def on_startup():
    # chạy task dọn phòng nền
    sio.start_background_task(cleanup_stale_rooms)

    # Khởi động Stockfish nếu có
    global engine
    if not STOCKFISH_PATH or not os.path.exists(STOCKFISH_PATH):
        log.warning("[AI_ENGINE] STOCKFISH_PATH is missing or invalid. AI games will not be available.")
        log.warning(f"[AI_ENGINE] Current path: {STOCKFISH_PATH}")
    else:
        try:
            engine = chess.engine.SimpleEngine.popen_uci(STOCKFISH_PATH)
            try:
                engine.configure({"Skill Level": ENGINE_SKILL})
                log.info(f"[AI_ENGINE] Stockfish configured with default skill level {ENGINE_SKILL}")
            except Exception as e:
                log.warning(f"[AI_ENGINE] Cannot set 'Skill Level': {e}")
            log.info(f"[AI_ENGINE] ✅ Stockfish engine ready at: {STOCKFISH_PATH}")
            log.info("[AI_ENGINE] 🤖 AI vs Human games are now available!")
        except Exception as e:
            log.error(f"[AI_ENGINE] ❌ Failed to start Stockfish at {STOCKFISH_PATH}: {e}")
            log.error("[AI_ENGINE] AI games will not be available until engine is fixed")

@app.on_event("shutdown")
async def on_shutdown():
    global engine
    if engine is not None:
        try:
            engine.quit()
        except Exception:
            pass
        engine = None

# ----------------------------
# Run
# ----------------------------
if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:asgi_app", host="0.0.0.0", port=PORT, reload=False)
