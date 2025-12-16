"""
Gemini AI 서비스 클래스
Google Gemini API를 사용하여 화장품 성분 정보를 생성합니다.
"""

import os
import logging
from typing import Optional
import google.generativeai as genai
from dotenv import load_dotenv

# .env 파일 로드
load_dotenv()

logger = logging.getLogger(__name__)


class GeminiService:
    """
    Gemini AI 서비스를 제공하는 클래스
    
    Google의 Gemini AI 모델을 사용하여 화장품 성분 정보를 생성하고 분석합니다.
    
    주요 기능:
    - 성분의 기능(purpose) 정보 생성
    - 영문 성분 설명을 한국어로 번역
    - 성분의 상세 설명 생성
    - 성분의 피부 타입 적합성 생성
    - 짧은 텍스트 번역
    - 제품 분석 리포트 개선
    
    사용 모델:
    - gemini-2.5-flash: 빠른 응답 속도를 위한 경량 모델
    """
    
    def __init__(self):
        """Gemini 서비스 초기화"""
        self.api_key = os.getenv("GEMINI_API_KEY")
        
        if not self.api_key:
            logger.warning("⚠️ GEMINI_API_KEY가 .env 파일에 설정되지 않았습니다.")
            logger.warning("Gemini AI 기능이 제한적으로 동작할 수 있습니다.")
            self.model = None
        else:
            # Gemini API 설정
            genai.configure(api_key=self.api_key)
            self.model = genai.GenerativeModel(
                model_name="gemini-2.5-flash",
                generation_config={
                    "temperature": 0.7,
                    "top_k": 40,
                    "top_p": 0.95,
                    "max_output_tokens": 1024,
                },
                safety_settings=[
                    {
                        "category": "HARM_CATEGORY_HARASSMENT",
                        "threshold": "BLOCK_ONLY_HIGH"
                    },
                    {
                        "category": "HARM_CATEGORY_HATE_SPEECH",
                        "threshold": "BLOCK_ONLY_HIGH"
                    },
                    {
                        "category": "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        "threshold": "BLOCK_ONLY_HIGH"
                    },
                    {
                        "category": "HARM_CATEGORY_DANGEROUS_CONTENT",
                        "threshold": "BLOCK_ONLY_HIGH"
                    },
                ]
            )
            logger.info("✅ Gemini AI 서비스 초기화 완료")
    
    def is_available(self) -> bool:
        """API 키가 사용 가능한지 확인"""
        return self.api_key is not None and self.api_key.strip() != "" and self.model is not None
    
    def generate_ingredient_purpose(self, ingredient_name: str) -> str:
        """
        성분의 기능(목적) 정보를 생성합니다.
        
        Args:
            ingredient_name: 기능 정보를 생성할 성분명
            
        Returns:
            성분의 기능 설명 (20자 이내), 실패 시 "정보를 불러올 수 없습니다."
        """
        if not self.is_available():
            logger.warning("API key is missing. Falling back.")
            return "정보를 불러올 수 없습니다."
        
        try:
            prompt = f"""
                화장품 성분 "{ingredient_name}"의 주요 기능을 20자 이내로 간단히 답해주세요.
                예: "피부 보습 및 수분 유지"
            """.strip()
            
            response = self.model.generate_content(prompt)
            result = response.text.strip() if response.text else "정보 생성 실패"
            return result[:20] if len(result) > 20 else result
        except Exception as e:
            logger.error(f"Error in generate_ingredient_purpose for: {ingredient_name}", exc_info=True)
            return "정보를 불러올 수 없습니다."
    
    def translate_ingredient_description(
        self, 
        ingredient_name: str, 
        english_description: str
    ) -> str:
        """
        영문 성분 설명을 한국어로 번역 및 요약합니다.
        
        Args:
            ingredient_name: 번역할 성분명
            english_description: 영문 설명 텍스트
            
        Returns:
            한국어로 번역된 설명, 실패 시 "설명을 불러올 수 없습니다."
        """
        if not self.is_available():
            logger.warning("API key is missing. Falling back.")
            return "설명을 불러올 수 없습니다."
        
        try:
            # 텍스트 길이에 따라 다른 프롬프트 사용
            if len(english_description) < 150:
                # 짧은 텍스트: 간단 번역
                prompt = f"""
                    다음 화장품 성분 설명을 자연스러운 한국어로 번역하세요:
                    "{english_description}"
                    
                    (50자 이내로 핵심만 간결하게 번역)
                """.strip()
            else:
                # 긴 텍스트: 요약 번역
                prompt = f"""
                    화장품 성분 "{ingredient_name}" 영문 설명을 한국어로 2문장 이내 요약:
                    {english_description[:500]}
                    
                    (100자 이내로 핵심만 답변)
                """.strip()
            
            response = self.model.generate_content(prompt)
            result = response.text.strip() if response.text else "설명을 생성할 수 없습니다."
            return result
        except Exception as e:
            logger.error(f"Error in translate_ingredient_description for: {ingredient_name}", exc_info=True)
            return "설명을 불러올 수 없습니다."
    
    def generate_ingredient_description(self, ingredient_name: str) -> str:
        """
        성분의 상세 설명을 생성합니다.
        
        Args:
            ingredient_name: 설명을 생성할 성분명
            
        Returns:
            성분의 상세 설명, 실패 시 "설명을 불러올 수 없습니다."
        """
        if not self.is_available():
            logger.warning("API key is missing. Falling back.")
            return "설명을 불러올 수 없습니다."
        
        try:
            prompt = f"""
                화장품 성분 "{ingredient_name}"의 효과와 적합 피부타입을 2문장으로 설명해주세요.
                (80자 이내로 간결하게)
            """.strip()
            
            response = self.model.generate_content(prompt)
            result = response.text.strip() if response.text else "상세 설명을 생성할 수 없습니다."
            return result
        except Exception as e:
            logger.error(f"Error in generate_ingredient_description for: {ingredient_name}", exc_info=True)
            return "설명을 불러올 수 없습니다."
    
    def generate_skin_type_suitability(self, ingredient_name: str) -> str:
        """
        성분의 피부 타입 적합성을 생성합니다.
        
        Args:
            ingredient_name: 적합성을 생성할 성분명
            
        Returns:
            피부 타입 적합성 문자열, 실패 시 "모든 피부 타입"
        """
        if not self.is_available():
            logger.warning("API key is missing. Falling back.")
            return "모든 피부 타입"
        
        try:
            prompt = f"""
                "{ingredient_name}" 적합 피부타입을 "권장: OO, 주의: OO" 형식으로 15자 이내 답변.
                (지성/건성/민감성/여드름성/중성 중 선택, 모두 적합시 "모든 피부")
            """.strip()
            
            response = self.model.generate_content(prompt)
            result = response.text.strip() if response.text else "모든 피부 타입"
            return result
        except Exception as e:
            logger.error(f"Error in generate_skin_type_suitability for: {ingredient_name}", exc_info=True)
            return "모든 피부 타입"
    
    def translate_short_text(self, text: str) -> str:
        """
        짧은 텍스트를 한국어로 번역합니다.
        
        Args:
            text: 번역할 텍스트 (영어 또는 기타 언어)
            
        Returns:
            한국어로 번역된 텍스트 (20자 이내), 실패 시 원문 반환
        """
        if not self.is_available():
            logger.warning("API key is missing. Falling back.")
            return text
        
        try:
            prompt = f"""
                다음 텍스트를 한국어로 20자 이내로 간결하게 번역해주세요.
                텍스트: "{text}"
                번역:
            """.strip()
            
            response = self.model.generate_content(prompt)
            result = response.text.strip() if response.text else text
            return result
        except Exception as e:
            logger.error("Error in translate_short_text", exc_info=True)
            return text
    
    def generate_user_friendly_explanation(
        self,
        ingredient_name: str,
        ingredient_type: str,
        original_reason: str
    ) -> str:
        """
        성분 뱃지 클릭 시 사용자가 이해하기 쉬운 설명을 생성합니다.
        
        Args:
            ingredient_name: 성분명
            ingredient_type: 성분 타입 ("good" 또는 "bad")
            original_reason: 원본 이유 설명
            
        Returns:
            사용자 친화적인 한국어 설명 (2-3문장), 실패 시 기본 메시지 반환
        """
        if not self.is_available():
            logger.warning("API key is missing. Falling back to default message.")
            return self._get_default_explanation(ingredient_type)
        
        try:
            if ingredient_type == "bad":
                prompt = f"""
                    화장품 성분 "{ingredient_name}"이(가) 왜 주의가 필요한 성분인지 일반인이 쉽게 이해할 수 있도록 설명해주세요.
                    
                    참고 정보: {original_reason}
                    
                    다음 조건을 따라주세요:
                    1. 전문 용어 없이 쉬운 한국어로 2-3문장으로 설명
                    2. 어떤 피부 타입이나 상황에서 주의해야 하는지 구체적으로 언급
                    3. 실질적인 조언 포함 (예: 패치 테스트 권장, 소량 사용 권장 등)
                    4. 너무 무섭게 쓰지 말고, 객관적이고 중립적인 톤 유지
                    5. 총 150자 이내로 작성
                    
                    예시: "이 성분은 민감한 피부에 자극을 줄 수 있어요. 특히 피부가 예민하거나 알레르기가 있다면 처음 사용 전 팔 안쪽에 먼저 테스트해보세요."
                """.strip()
            elif ingredient_type == "good":
                prompt = f"""
                    화장품 성분 "{ingredient_name}"이(가) 왜 좋은 성분인지 일반인이 쉽게 이해할 수 있도록 설명해주세요.
                    
                    참고 정보: {original_reason}
                    
                    다음 조건을 따라주세요:
                    1. 전문 용어 없이 쉬운 한국어로 2-3문장으로 설명
                    2. 이 성분이 피부에 어떤 좋은 효과를 주는지 구체적으로 언급
                    3. 어떤 피부 고민에 도움이 되는지 포함
                    4. 긍정적이지만 과장하지 않는 톤 유지
                    5. 총 150자 이내로 작성
                    
                    예시: "피부 깊숙이 수분을 채워주는 보습 성분이에요. 건조하거나 당기는 피부에 촉촉함을 오래 유지시켜줍니다."
                """.strip()
            else:
                prompt = f"""
                    화장품 성분 "{ingredient_name}"에 대해 일반인이 쉽게 이해할 수 있도록 2-3문장으로 설명해주세요.
                    참고 정보: {original_reason}
                    총 150자 이내로 작성해주세요.
                """.strip()
            
            response = self.model.generate_content(prompt)
            result = response.text.strip() if response.text else self._get_default_explanation(ingredient_type)
            return result
        except Exception as e:
            logger.error(f"Error in generate_user_friendly_explanation for: {ingredient_name}", exc_info=True)
            return self._get_default_explanation(ingredient_type)
    
    def enhance_product_analysis_summary(
        self,
        server_report: str,
        ingredients: list,
        good_matches: list,
        bad_matches: list
    ) -> str:
        """
        RAG 서버 분석 리포트를 개선합니다.
        
        Args:
            server_report: RAG 서버로부터 받은 분석 리포트
            ingredients: 전체 성분 리스트
            good_matches: 좋은 성분명 리스트
            bad_matches: 주의 성분명 리스트
            
        Returns:
            개선된 분석 리포트, 실패 시 서버 리포트 반환
        """
        # 서버 리포트가 충분히 상세하면 그대로 사용
        if len(server_report) > 100 and "분석 중" not in server_report and "오류" not in server_report:
            return server_report
        
        if not self.is_available():
            logger.warning("API key is missing. Using server report.")
            return server_report
        
        try:
            ingredients_list = ", ".join(ingredients[:5])
            good_ingredients = ", ".join(good_matches[:3]) if good_matches else "없음"
            bad_ingredients = ", ".join(bad_matches[:2]) if bad_matches else "없음"
            
            prompt = f"""
                다음 화장품의 성분을 분석하여 종합 평가를 3-4 문장으로 작성해주세요.
                
                전체 성분: {ingredients_list}
                좋은 성분: {good_ingredients}
                주의 성분: {bad_ingredients}
                
                다음 내용을 포함해주세요:
                1. 제품의 주요 효능 (보습, 진정, 미백 등)
                2. 어떤 피부 타입에 적합한지
                3. 주의해야 할 성분이 있다면 간단히 언급
                4. 전반적인 제품 평가
                
                전문적이면서도 이해하기 쉽게 작성해주세요.
            """.strip()
            
            response = self.model.generate_content(prompt)
            result = response.text.strip() if response.text else server_report
            return result
        except Exception as e:
            logger.error("Error in enhance_product_analysis_summary", exc_info=True)
            return server_report
    
    def _get_default_explanation(self, ingredient_type: str) -> str:
        """기본 설명 메시지 반환"""
        if ingredient_type == "bad":
            return "이 성분은 일부 피부 타입에 자극을 줄 수 있어요. 민감한 피부라면 먼저 소량으로 테스트해보시는 것을 권장합니다."
        elif ingredient_type == "good":
            return "피부에 좋은 효과를 주는 성분이에요. 꾸준히 사용하면 피부 개선에 도움이 됩니다."
        else:
            return "이 성분에 대한 정보입니다."

