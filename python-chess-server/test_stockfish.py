import chess
import chess.engine

# Đặt đúng đường dẫn tới stockfish.exe
STOCKFISH_PATH = r"C:\Users\NC\Downloads\stockfish-windows-x86-64-avx2\stockfish\stockfish-windows-x86-64-avx2.exe"

def test_engine():
    try:
        print(f"Đang khởi động Stockfish từ: {STOCKFISH_PATH}")
        engine = chess.engine.SimpleEngine.popen_uci(STOCKFISH_PATH)

        # Khởi tạo bàn cờ mặc định
        board = chess.Board()
        print("Bàn cờ bắt đầu:", board)

        # Yêu cầu Stockfish tính 1 nước trong 0.1 giây
        result = engine.play(board, chess.engine.Limit(time=0.1))
        print("Nước đi gợi ý:", result.move)

        engine.quit()
        print("✅ Stockfish chạy OK")
    except Exception as e:
        print("❌ Lỗi khi chạy Stockfish:", e)

if __name__ == "__main__":
    test_engine()
