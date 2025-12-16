"""
대화 메모리 모듈
채팅 세션 관리
"""

import uuid
from typing import Dict
from datetime import datetime


class SimpleConversationMemory:
    """
    간단한 대화 메모리 클래스
    
    채팅 세션의 대화 히스토리를 메모리에 저장합니다.
    각 메시지는 입력, 출력, 타임스탬프를 포함합니다.
    
    Attributes:
        messages: 대화 메시지 리스트
    """
    def __init__(self):
        """대화 메모리 초기화"""
        self.messages = []
    
    def save_context(self, inputs: Dict, outputs: Dict):
        """
        대화 컨텍스트를 저장합니다.
        
        Args:
            inputs: 입력 딕셔너리 (예: {'input': '질문'})
            outputs: 출력 딕셔너리 (예: {'output': '답변'})
        """
        self.messages.append({
            'input': inputs.get('input', ''),
            'output': outputs.get('output', ''),
            'timestamp': datetime.now().isoformat()
        })
    
    def clear(self):
        """대화 히스토리를 모두 삭제합니다."""
        self.messages.clear()
    
    @property
    def chat_memory(self):
        """
        채팅 메모리 객체를 반환합니다.
        
        Returns:
            자기 자신 (LangChain 호환성을 위해)
        """
        return self


class ConversationManager:
    """
    대화 세션 관리자
    
    여러 채팅 세션을 관리합니다.
    """
    
    def __init__(self):
        """대화 관리자 초기화"""
        self.chat_sessions = {}
    
    def get_or_create_session(self, session_id: str = None) -> str:
        """
        채팅 세션을 가져오거나 새로 생성합니다.
        
        Args:
            session_id: 세션 ID (없으면 새로 생성)
        
        Returns:
            세션 ID
        """
        if session_id is None:
            session_id = str(uuid.uuid4())
        if session_id not in self.chat_sessions:
            self.chat_sessions[session_id] = SimpleConversationMemory()
        return session_id
    
    def get_session(self, session_id: str) -> SimpleConversationMemory:
        """
        세션을 가져옵니다.
        
        Args:
            session_id: 세션 ID
        
        Returns:
            대화 메모리 객체
        """
        return self.chat_sessions.get(session_id)

