"""
Mock LLM 클래스
제품 분석 리포트 생성용 규칙 기반 LLM
"""

import re
from typing import List, Optional, Any
from langchain_core.language_models.llms import LLM
from langchain_core.callbacks.manager import CallbackManagerForLLMRun


class MockLLM(LLM):
    """
    Mock LLM 클래스 - 제품 분석 리포트 생성
    
    실제 LLM 대신 규칙 기반으로 제품 분석 리포트를 생성합니다.
    LangChain의 LLM 인터페이스를 구현하여 RAG 파이프라인에서 사용됩니다.
    
    생성 로직:
    - 프롬프트에서 피부 타입, 좋은 성분, 주의 성분을 추출
    - 규칙 기반으로 자연스러운 한국어 리포트 생성
    """
    
    @property
    def _llm_type(self) -> str:
        """
        LLM 타입을 반환합니다.
        
        Returns:
            "mock" (Mock LLM임을 나타냄)
        """
        return "mock"
    
    def _call(
        self,
        prompt: str,
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> str:
        """
        프롬프트를 처리하여 응답을 생성합니다.
        
        현재는 "종합 분석 리포트" 생성만 지원합니다.
        다른 프롬프트는 기본 메시지를 반환합니다.
        
        Args:
            prompt: 입력 프롬프트
            stop: 중지 토큰 리스트 (사용 안 함)
            run_manager: 콜백 매니저 (사용 안 함)
            **kwargs: 추가 인자
        
        Returns:
            생성된 리포트 문자열
        """
        if "종합 분석 리포트" in prompt or "종합 분석" in prompt:
            return self._generate_product_analysis(prompt)
        
        return "해당 성분에 대한 정보를 찾을 수 없습니다."
    
    def _generate_product_analysis(self, prompt: str) -> str:
        """
        제품 종합 분석 리포트를 생성합니다.
        
        프롬프트에서 다음 정보를 추출하여 리포트를 생성합니다:
        - 사용자 피부 타입
        - 좋은 성분 목록
        - 주의 성분 목록
        - 주요 성분 목적
        
        리포트 구조:
        1. 제품 타입 추론 (보습, 항산화, 각질 제거 등)
        2. 긍정적 분석 (좋은 성분 언급)
        3. 주의 성분 분석
        4. 종합 평가
        
        Args:
            prompt: 분석 리포트 생성 프롬프트
        
        Returns:
            생성된 분석 리포트 (한국어)
        """
        # 피부 타입 추출
        skin_type_match = re.search(r'사용자 피부 타입:\s*([^\n]+)', prompt)
        skin_type = skin_type_match.group(1).strip() if skin_type_match else "알 수 없는"
        
        # 좋은 성분 목록 추출
        good_match_str = re.search(r'\[.*?\]에 좋은 성분 목록:\s*([^\n]+)', prompt)
        if not good_match_str:
            good_match_str = re.search(r'좋은 성분 목록:\s*([^\n]+)', prompt)
        good_names = good_match_str.group(1).strip() if good_match_str else ""
        if good_names == "없음":
            good_names = ""
        
        # 주의 성분 목록 추출
        bad_match_str = re.search(r'주의 성분 목록 \(일반적 포함\):\s*([^\n]+)', prompt)
        if not bad_match_str:
            bad_match_str = re.search(r'주의 성분 목록:\s*([^\n]+)', prompt)
        bad_names = bad_match_str.group(1).strip() if bad_match_str else ""
        if bad_names == "없음":
            bad_names = ""
        
        # 리포트 생성
        report_parts = []
        
        # 제품 타입 추론
        purpose_match = re.search(r'주요 성분 목적\):\s*([^\n]+)', prompt)
        main_purpose = "복합적인"
        
        if purpose_match:
            purposes = purpose_match.group(1).strip()
            purpose_map = {
                "moisturizer": "보습", "antioxidant": "항산화",
                "exfoliant": "각질 제거", "fragrance": "향료",
                "preservative": "보존", "emulsifier": "유화"
            }
            first_purpose_match = re.search(r'([a-zA-Z가-힣\s]+)\s*\(\d+회\)', purposes)
            if first_purpose_match:
                purpose_name = first_purpose_match.group(1).strip().lower()
                main_purpose = purpose_map.get(purpose_name, purpose_name)
        
        report_parts.append(f"이 화장품은(는) '{main_purpose}'에 중점을 둔 제품으로 보입니다.")
        
        # 긍정적 분석
        if good_names:
            good_names_list = [n.strip() for n in good_names.split(',') if n.strip()][:3]
            good_names_short = ", ".join(good_names_list)
            if len([n.strip() for n in good_names.split(',') if n.strip()]) > 3:
                good_names_short += " 등"
            report_parts.append(f"특히 {skin_type} 피부에 좋은 {good_names_short} 성분이 포함되어 있습니다.")
        
        # 주의 성분 분석
        if bad_names:
            bad_names_list = [n.strip() for n in bad_names.split(',') if n.strip()][:2]
            bad_names_short = ", ".join(bad_names_list)
            report_parts.append(f"다만, {bad_names_short} 성분은 일부 피부에 자극을 줄 수 있으니 참고하세요.")
        
        # 종합 평가
        if good_names and not bad_names:
            report_parts.append(f"전반적으로 {skin_type} 피부에 좋은 제품으로 평가됩니다.")
        elif good_names and bad_names:
            report_parts.append(f"사용 시 피부 반응을 주의 깊게 관찰하시기 바랍니다.")
        else:
            report_parts.append(f"개인적인 피부 반응을 확인하며 사용하시기 바랍니다.")
        
        return " ".join(report_parts)

