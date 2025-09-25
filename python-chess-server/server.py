import os
import asyncio
import logging
import random
import string
import time
from typing import Dict, List, Optional, TypedDict, Literal

import socketio
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

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

# ----------------------------
# Server state (in-memory)
# ----------------------------
rooms: Dict[str, RoomState] = {}
# Map sid -> membership
memberships: Dict[str, Dict[str, str]] = {}

# ----------------------------
# FastAPI + Socket.IO (ASGI)
# ----------------------------
app = FastAPI(title="Chess Server (Python)")

# CORS giống origin: "*"
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

PORT = int(os.environ.get("PORT", "4000"))

# ----------------------------
# Helpers
# ----------------------------
def now_ms() -> float:
    return time.time() * 1000.0

def gen_code() -> str:
    """Generate a six character uppercase room code."""
    return "".join(random.choice(string.ascii_uppercase) for _ in range(6))

def initial_state() -> GameState:
    return {
        "lastMove": None,
        "boardFEN": "startpos",
    }

def create_room(code: str) -> RoomState:
    return {
        "code": code,
        "players": [],
        "state": initial_state(),
        "sideToMove": "WHITE",
        "lastActive": now_ms(),
    }

def validate_move_payload(move: dict):
    # Map field 'from' của JS sang 'from_' trong Python cho TypedDict
    if "from" in move and "from_" not in move:
        move["from_"] = move["from"]
    if not isinstance(move.get("from_"), int) or not isinstance(move.get("to"), int):
        raise ValueError("Move coordinates must be integers")
    f, t = move["from_"], move["to"]
    if f < 0 or f > 63 or t < 0 or t > 63:
        raise ValueError("Squares must be between 0 and 63")
    if f == t:
        raise ValueError("From and to squares must differ")

    promo = move.get("promo", None)
    if promo is not None and promo not in ("Q", "R", "B", "N"):
        raise ValueError("Promotion piece must be Q, R, B, or N")

    for flag in ("isCastle", "isEnPassant", "isDoublePawnPush"):
        if flag in move and not isinstance(move[flag], bool):
            raise ValueError(f"{flag} must be boolean")

def validate_legal_move(state: GameState, move: ChessMove) -> bool:
    # Placeholder như bản JS (chưa validate luật cờ thật)
    return True

def apply_move_server(room: RoomState, move: ChessMove):
    room["state"]["lastMove"] = {
        "from_": move["from_"],
        "to": move["to"],
        "promo": move.get("promo") or None,
        "isCastle": move.get("isCastle") or False,
        "isEnPassant": move.get("isEnPassant") or False,
        "isDoublePawnPush": move.get("isDoublePawnPush") or False,
    }
    room["sideToMove"] = "BLACK" if room["sideToMove"] == "WHITE" else "WHITE"

async def emit_room_state(room: RoomState):
    payload = {
        "code": room["code"],
        "players": [{"uid": p["uid"], "color": p["color"]} for p in room["players"]],
        "sideToMove": room["sideToMove"],
        "state": room["state"],
    }
    await sio.emit("room_state", payload, room=room["code"])

async def remove_membership_and_update_room(sid: str):
    membership = memberships.get(sid)
    if not membership:
        return

    code, uid = membership["code"], membership["uid"]
    room = rooms.get(code)
    if not room:
        memberships.pop(sid, None)
        return

    # Chỉ xóa player nếu sid rời trùng với socket hiện tại của player (tránh vụ reconnect)
    idx = next((i for i, p in enumerate(room["players"]) if p["uid"] == uid), -1)
    if idx != -1:
        player = room["players"][idx]
        if player["socketId"] != sid:
            # Đây là disconnect cũ (stale), bỏ qua
            memberships.pop(sid, None)
            return
        room["players"].pop(idx)
        room["lastActive"] = now_ms()
        await emit_room_state(room)

    if len(room["players"]) == 0:
        # Reset lại nếu phòng trống
        room["sideToMove"] = "WHITE"
        room["state"] = initial_state()

    memberships.pop(sid, None)

# ----------------------------
# REST: POST /room
# ----------------------------
@app.post("/room")
async def create_room_endpoint():
    # tạo mã code duy nhất
    code = gen_code()
    while code in rooms:
        code = gen_code()
    rooms[code] = create_room(code)
    log.info(f"[DEBUG] Room created: {code}")
    log.info(f"[DEBUG] Total active rooms: {len(rooms)}")
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
        log.info(f"[DEBUG] Join room request from {sid}: {payload}")
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
            log.info(f"[DEBUG] Player {uid} reconnected to room {code} as {player['color']}")
        else:
            color: Color = "WHITE" if len(room["players"]) == 0 else "BLACK"
            player = {"uid": uid, "color": color, "socketId": sid}
            room["players"].append(player)
            log.info(f"[DEBUG] Player {uid} joined room {code} as {color}")

        memberships[sid] = {"code": code, "uid": uid}
        await sio.enter_room(sid, code)
        room["lastActive"] = now_ms()
        log.info(f"[DEBUG] Room {code} now has {len(room['players'])} players")

        # Gửi room_joined cho người mới vào
        await sio.emit(
            "room_joined",
            {"code": room["code"], "color": player["color"].lower()},
            to=sid,
        )

        # Nếu là người thứ 2 (và không phải reconnect), thông báo cho người còn lại
        if not existing and len(room["players"]) == 2:
            other = next((p for p in room["players"] if p["uid"] != uid), None)
            if other:
                await sio.emit("opponent_joined", {}, to=other["socketId"])

        await emit_room_state(room)

    except Exception as e:
        log.info(f"[DEBUG] Join room error for {sid}: {e}")
        await sio.emit("error_msg", {"message": str(e) or "Failed to join room"}, to=sid)

@sio.event
async def move(sid, payload):
    try:
        log.info(f"[DEBUG] Move request from {sid}: {payload}")
        membership = memberships.get(sid)
        if not membership:
            raise ValueError("You are not joined to a room")

        code = payload.get("code") if isinstance(payload, dict) else None
        move_obj = payload.get("move") if isinstance(payload, dict) else None

        if not code or not isinstance(code, str):
            raise ValueError("Room code is required")
        if membership["code"] != code:
            raise ValueError("You are not part of this room")
        if not isinstance(move_obj, dict):
            raise ValueError("Move payload is required")

        room = rooms.get(code)
        if not room:
            raise ValueError("Room not found")

        player = next((p for p in room["players"] if p["uid"] == membership["uid"]), None)
        if not player:
            raise ValueError("Player not found in room")
        if player["color"] != room["sideToMove"]:
            raise ValueError("Not your turn")

        # Chuẩn hóa + validate move
        if "from" in move_obj and "from_" not in move_obj:
            move_obj["from_"] = move_obj["from"]
        validate_move_payload(move_obj)
        # Placeholder: luôn hợp lệ
        if not validate_legal_move(room["state"], move_obj):  # pragma: no cover
            raise ValueError("Illegal move")

        apply_move_server(room, move_obj)  # cập nhật state + flip lượt

        response = {
            "move": move_obj,
            "sideToMove": room["sideToMove"],
            "state": room["state"],
        }
        await sio.emit("move_applied", response, room=code)
        room["lastActive"] = now_ms()

    except Exception as e:
        log.info(f"[DEBUG] Move error for {sid}: {e}")
        await sio.emit("error_msg", {"message": str(e) or "Failed to apply move"}, to=sid)

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
                log.info(f"[DEBUG] Cleaned stale room: {code}")
        except Exception as e:
            log.warning(f"[DEBUG] Cleanup error: {e}")
        await asyncio.sleep(60)

@app.on_event("startup")
async def on_startup():
    # chạy task dọn phòng nền
    sio.start_background_task(cleanup_stale_rooms)

# ----------------------------
# Run
# ----------------------------
if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:asgi_app", host="0.0.0.0", port=PORT, reload=False)
