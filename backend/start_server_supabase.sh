#!/bin/bash

# 화장품 성분 RAG 서버 시작 스크립트 (Supabase 버전)
# 사용법: ./start_server_supabase.sh

echo "=========================================="
echo "🚀 화장품 성분 RAG 서버 시작 (Supabase 버전)"
echo "=========================================="

# 스크립트 디렉토리로 이동
cd "$(dirname "$0")"

# 가상환경 활성화
if [ -d "venv" ]; then
    echo "📦 가상환경 활성화 중..."
    source venv/bin/activate
else
    echo "⚠️ 가상환경이 없습니다. 생성 중..."
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
fi

# .env 파일 확인
if [ ! -f ".env" ]; then
    echo ""
    echo "⚠️ .env 파일이 없습니다!"
    echo "   env_example.txt를 참고하여 .env 파일을 생성하세요."
    echo ""
    echo "   1. Supabase 프로젝트 생성: https://supabase.com"
    echo "   2. SQL Editor에서 SUPABASE_SETUP.sql 실행"
    echo "   3. .env 파일 생성 후 SUPABASE_URL, SUPABASE_KEY 설정"
    echo ""
    echo "   Supabase 없이 JSON 모드로 시작하시겠습니까? (y/n)"
    read -r answer
    if [ "$answer" != "y" ]; then
        exit 1
    fi
fi

echo ""
echo "📊 서버 시작..."
echo "   API 문서: http://localhost:5000/docs"
echo "   헬스체크: http://localhost:5000/health"
echo ""

# 서버 시작 (Supabase 버전)
python rag_server_supabase.py

