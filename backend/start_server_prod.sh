#!/bin/bash

# 화장품 성분 RAG 서버 시작 스크립트 (프로덕션 모드)

echo "🚀 화장품 성분 RAG 서버 시작 중 (프로덕션 모드)..."

# 스크립트 디렉토리로 이동
cd "$(dirname "$0")"

# 가상환경 활성화 (있는 경우)
if [ -d "venv" ]; then
    echo "📦 가상환경 활성화 중..."
    source venv/bin/activate
fi

# CPU 코어 수 확인
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    WORKERS=$(sysctl -n hw.ncpu)
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux
    WORKERS=$(nproc)
else
    # 기본값
    WORKERS=4
fi

# 워커 수 계산 (코어 수 * 2 + 1)
WORKERS=$((WORKERS * 2 + 1))

echo "🔧 워커 수: $WORKERS"
echo "📚 의존성 확인 중..."
pip install -q -r requirements.txt

echo "✅ FastAPI 서버 실행 중 (프로덕션 모드)..."
echo "📱 서버 주소: http://0.0.0.0:5000"
echo "📚 API 문서: http://localhost:5000/docs"
echo ""

# 프로덕션 모드 (멀티 워커)
uvicorn rag_server_fastapi:app --host 0.0.0.0 --port 5000 --workers $WORKERS

