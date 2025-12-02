#!/bin/bash

# 화장품 성분 RAG 서버 시작 스크립트 (FastAPI)

echo "🚀 화장품 성분 RAG 서버 시작 중..."

# 스크립트 디렉토리로 이동
cd "$(dirname "$0")"

# 가상환경 활성화 (있는 경우)
if [ -d "venv" ]; then
    echo "📦 가상환경 활성화 중..."
    source venv/bin/activate
fi

# Python 버전 확인
echo "🐍 Python 버전:"
python --version

# 의존성 확인
echo "📚 필요한 패키지 확인 중..."
pip install -q -r requirements.txt

# 서버 실행
echo "✅ FastAPI 서버 실행 중..."
echo "📱 Android 앱에서 http://localhost:5000 으로 접속하세요"
echo "📚 API 문서: http://localhost:5000/docs"
echo ""

# 개발 모드 (자동 리로드)
uvicorn rag_server_fastapi:app --host 0.0.0.0 --port 5000 --reload

