#!/usr/bin/env python3
"""
RAG 시스템 평가 모듈
엔터프라이즈급 RAG 파이프라인의 성능을 평가하기 위한 핵심 지표들을 정의합니다.
"""

from typing import List, Dict, Any, Tuple
from dataclasses import dataclass
import numpy as np
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity


@dataclass
class EvaluationResult:
    """RAG 평가 결과를 저장하는 데이터 클래스"""
    context_relevancy_score: float
    faithfulness_score: float
    answer_relevancy_score: float
    overall_score: float
    details: Dict[str, Any]


class RAGEvaluator:
    """
    RAG 시스템의 성능을 평가하는 클래스
    
    핵심 평가 지표:
    1. Context Relevancy: 검색된 컨텍스트가 질문과 얼마나 관련이 있는가?
    2. Faithfulness: 생성된 답변이 검색된 컨텍스트에 얼마나 충실한가?
    3. Answer Relevancy: 생성된 답변이 원본 질문에 얼마나 관련이 있는가?
    """
    
    def __init__(self):
        """평가 모델 초기화"""
        self.similarity_model = SentenceTransformer('sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2')
    
    def evaluate_context_relevancy(self, query: str, retrieved_contexts: List[str]) -> float:
        """
        Context Relevancy 평가
        
        검색된 컨텍스트가 사용자 질문과 얼마나 관련이 있는지 측정합니다.
        높은 점수는 검색된 컨텍스트가 질문과 더 관련성이 높음을 의미합니다.
        
        Args:
            query: 사용자 질문
            retrieved_contexts: 검색된 컨텍스트 리스트
            
        Returns:
            float: 0.0 ~ 1.0 사이의 관련성 점수
        """
        if not retrieved_contexts:
            return 0.0
        
        # 쿼리와 각 컨텍스트 간의 유사도 계산
        query_embedding = self.similarity_model.encode([query])
        context_embeddings = self.similarity_model.encode(retrieved_contexts)
        
        similarities = cosine_similarity(query_embedding, context_embeddings)[0]
        
        # 평균 유사도 반환
        return float(np.mean(similarities))
    
    def evaluate_faithfulness(self, generated_answer: str, retrieved_contexts: List[str]) -> float:
        """
        Faithfulness 평가
        
        생성된 답변이 검색된 컨텍스트에 얼마나 충실한지 측정합니다.
        높은 점수는 답변이 컨텍스트 정보에 더 충실함을 의미합니다.
        
        Args:
            generated_answer: 생성된 답변
            retrieved_contexts: 검색된 컨텍스트 리스트
            
        Returns:
            float: 0.0 ~ 1.0 사이의 충실도 점수
        """
        if not retrieved_contexts:
            return 0.0
        
        # 답변과 각 컨텍스트 간의 유사도 계산
        answer_embedding = self.similarity_model.encode([generated_answer])
        context_embeddings = self.similarity_model.encode(retrieved_contexts)
        
        similarities = cosine_similarity(answer_embedding, context_embeddings)[0]
        
        # 최대 유사도 반환 (답변이 가장 관련 있는 컨텍스트와 얼마나 유사한가?)
        return float(np.max(similarities))
    
    def evaluate_answer_relevancy(self, query: str, generated_answer: str) -> float:
        """
        Answer Relevancy 평가
        
        생성된 답변이 원본 질문에 얼마나 관련이 있는지 측정합니다.
        높은 점수는 답변이 질문에 더 적절하게 응답함을 의미합니다.
        
        Args:
            query: 사용자 질문
            generated_answer: 생성된 답변
            
        Returns:
            float: 0.0 ~ 1.0 사이의 관련성 점수
        """
        # 질문과 답변 간의 유사도 계산
        query_embedding = self.similarity_model.encode([query])
        answer_embedding = self.similarity_model.encode([generated_answer])
        
        similarity = cosine_similarity(query_embedding, answer_embedding)[0][0]
        return float(similarity)
    
    def evaluate_rag_system(self, 
                           query: str, 
                           retrieved_contexts: List[str], 
                           generated_answer: str) -> EvaluationResult:
        """
        RAG 시스템의 종합적인 성능 평가
        
        Args:
            query: 사용자 질문
            retrieved_contexts: 검색된 컨텍스트 리스트
            generated_answer: 생성된 답변
            
        Returns:
            EvaluationResult: 평가 결과 객체
        """
        # 각 지표별 점수 계산
        context_relevancy = self.evaluate_context_relevancy(query, retrieved_contexts)
        faithfulness = self.evaluate_faithfulness(generated_answer, retrieved_contexts)
        answer_relevancy = self.evaluate_answer_relevancy(query, generated_answer)
        
        # 종합 점수 계산 (가중 평균)
        overall_score = (context_relevancy * 0.3 + 
                        faithfulness * 0.4 + 
                        answer_relevancy * 0.3)
        
        return EvaluationResult(
            context_relevancy_score=context_relevancy,
            faithfulness_score=faithfulness,
            answer_relevancy_score=answer_relevancy,
            overall_score=overall_score,
            details={
                'query': query,
                'num_contexts': len(retrieved_contexts),
                'answer_length': len(generated_answer),
                'evaluation_timestamp': '2024-01-01T00:00:00Z'  # 실제로는 datetime.now().isoformat()
            }
        )
    
    def batch_evaluate(self, test_cases: List[Dict[str, Any]]) -> List[EvaluationResult]:
        """
        여러 테스트 케이스에 대한 배치 평가
        
        Args:
            test_cases: [{'query': str, 'contexts': List[str], 'answer': str}, ...]
            
        Returns:
            List[EvaluationResult]: 각 테스트 케이스의 평가 결과
        """
        results = []
        for test_case in test_cases:
            result = self.evaluate_rag_system(
                query=test_case['query'],
                retrieved_contexts=test_case['contexts'],
                generated_answer=test_case['answer']
            )
            results.append(result)
        
        return results
    
    def generate_evaluation_report(self, results: List[EvaluationResult]) -> Dict[str, Any]:
        """
        평가 결과를 종합한 리포트 생성
        
        Args:
            results: 평가 결과 리스트
            
        Returns:
            Dict: 종합 평가 리포트
        """
        if not results:
            return {'error': '평가 결과가 없습니다.'}
        
        # 평균 점수 계산
        avg_context_relevancy = np.mean([r.context_relevancy_score for r in results])
        avg_faithfulness = np.mean([r.faithfulness_score for r in results])
        avg_answer_relevancy = np.mean([r.answer_relevancy_score for r in results])
        avg_overall = np.mean([r.overall_score for r in results])
        
        return {
            'summary': {
                'total_evaluations': len(results),
                'average_context_relevancy': float(avg_context_relevancy),
                'average_faithfulness': float(avg_faithfulness),
                'average_answer_relevancy': float(avg_answer_relevancy),
                'average_overall_score': float(avg_overall)
            },
            'performance_level': self._get_performance_level(float(avg_overall)),
            'recommendations': self._generate_recommendations(results)
        }
    
    def _get_performance_level(self, overall_score: float) -> str:
        """전체 점수에 따른 성능 레벨 반환"""
        if overall_score >= 0.8:
            return "Excellent"
        elif overall_score >= 0.6:
            return "Good"
        elif overall_score >= 0.4:
            return "Fair"
        else:
            return "Poor"
    
    def _generate_recommendations(self, results: List[EvaluationResult]) -> List[str]:
        """평가 결과를 바탕으로 개선 권고사항 생성"""
        recommendations = []
        
        avg_context = np.mean([r.context_relevancy_score for r in results])
        avg_faithfulness = np.mean([r.faithfulness_score for r in results])
        avg_answer = np.mean([r.answer_relevancy_score for r in results])
        
        if avg_context < 0.6:
            recommendations.append("검색 컨텍스트의 관련성을 높이기 위해 임베딩 모델이나 검색 전략을 개선하세요.")
        
        if avg_faithfulness < 0.6:
            recommendations.append("답변 생성 시 컨텍스트 정보를 더 충실히 반영하도록 프롬프트를 개선하세요.")
        
        if avg_answer < 0.6:
            recommendations.append("사용자 질문에 대한 답변의 관련성을 높이기 위해 답변 생성 로직을 개선하세요.")
        
        if not recommendations:
            recommendations.append("전체적인 성능이 양호합니다. 지속적인 모니터링을 권장합니다.")
        
        return recommendations


# 사용 예시 및 테스트 함수들
def create_sample_test_cases() -> List[Dict[str, Any]]:
    """샘플 테스트 케이스 생성"""
    return [
        {
            'query': '니아신아마이드는 어떤 효과가 있나요?',
            'contexts': [
                '니아신아마이드는 비타민 B3의 한 형태로, 피부 진정과 수분 공급에 효과적입니다.',
                '니아신아마이드는 모공 축소와 색소침착 개선에 도움을 줍니다.'
            ],
            'answer': '니아신아마이드는 비타민 B3의 한 형태로, 피부 진정, 수분 공급, 모공 축소, 색소침착 개선 등의 효과가 있습니다.'
        },
        {
            'query': '히알루론산은 무엇인가요?',
            'contexts': [
                '히알루론산은 천연 보습 성분으로 피부에 수분을 공급하는 역할을 합니다.',
                '히알루론산은 피부 탄력 개선과 주름 완화에 효과적입니다.'
            ],
            'answer': '히알루론산은 천연 보습 성분으로 피부 수분 공급과 탄력 개선에 효과적입니다.'
        }
    ]


def demo_evaluation():
    """평가 시스템 데모 실행"""
    print("🔍 RAG 시스템 평가 데모 시작")
    
    evaluator = RAGEvaluator()
    test_cases = create_sample_test_cases()
    
    # 개별 평가 실행
    results = evaluator.batch_evaluate(test_cases)
    
    # 결과 출력
    for i, result in enumerate(results, 1):
        print(f"\n📊 테스트 케이스 {i} 평가 결과:")
        print(f"  Context Relevancy: {result.context_relevancy_score:.3f}")
        print(f"  Faithfulness: {result.faithfulness_score:.3f}")
        print(f"  Answer Relevancy: {result.answer_relevancy_score:.3f}")
        print(f"  Overall Score: {result.overall_score:.3f}")
    
    # 종합 리포트 생성
    report = evaluator.generate_evaluation_report(results)
    print(f"\n📈 종합 평가 리포트:")
    print(f"  총 평가 수: {report['summary']['total_evaluations']}")
    print(f"  평균 Context Relevancy: {report['summary']['average_context_relevancy']:.3f}")
    print(f"  평균 Faithfulness: {report['summary']['average_faithfulness']:.3f}")
    print(f"  평균 Answer Relevancy: {report['summary']['average_answer_relevancy']:.3f}")
    print(f"  평균 Overall Score: {report['summary']['average_overall_score']:.3f}")
    print(f"  성능 레벨: {report['performance_level']}")
    print(f"  개선 권고사항: {', '.join(report['recommendations'])}")


if __name__ == "__main__":
    demo_evaluation()
