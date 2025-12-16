"""
벡터 스토어 모듈
ChromaDB 벡터 스토어 관리
"""

import logging
from typing import List, Dict

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.vectorstores import Chroma
from langchain_community.embeddings import SentenceTransformerEmbeddings

logger = logging.getLogger(__name__)


class VectorStore:
    """
    ChromaDB 벡터 스토어 관리 클래스
    
    성분 정보를 임베딩하여 벡터 스토어에 저장하고 검색합니다.
    """
    
    def __init__(self, ingredients_data: List[Dict], persist_directory: str = "./chroma_db_ingredients"):
        """
        벡터 스토어 초기화
        
        Args:
            ingredients_data: 성분 데이터 리스트
            persist_directory: ChromaDB 벡터 스토어 저장 디렉토리
        """
        self.ingredients_data = ingredients_data
        self.persist_directory = persist_directory
        self.text_splitter = None
        self.embeddings = None
        self.vectorstore = None
        self.retriever = None
        self._initialize()
    
    def _initialize(self):
        """
        벡터 스토어를 초기화합니다.
        
        초기화 과정:
        1. LangChain 컴포넌트 초기화
        2. 문서 생성 및 청크 분할
        3. ChromaDB에 저장
        4. Retriever 생성
        """
        logger.info("🔧 LangChain 컴포넌트 초기화 중...")
        
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000, chunk_overlap=200
        )
        
        self.embeddings = SentenceTransformerEmbeddings(
            model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
        )
        
        logger.info("🗄️ ChromaDB 벡터 스토어 생성 중...")
        self._create_vectorstore()
        logger.info("✅ LangChain 컴포넌트 초기화 완료")
    
    def _create_vectorstore(self):
        """
        ChromaDB 벡터 스토어를 생성합니다.
        
        각 성분 정보를 Document로 변환하여 벡터 스토어에 저장합니다.
        """
        documents = []
        for item in self.ingredients_data:
            kor_name = item.get('kor_name', '')
            eng_name = item.get('eng_name', '')
            description = item.get('description', '')
            purpose = item.get('purpose', [])
            good_for = item.get('good_for', [])
            bad_for = item.get('bad_for', [])
            
            content_parts = []
            if kor_name:
                content_parts.append(f"한국어 성분명: {kor_name}")
            if eng_name:
                content_parts.append(f"영어 성분명: {eng_name}")
            if description:
                content_parts.append(f"설명: {description[:500]}")
            if purpose:
                content_parts.append(f"목적: {', '.join(purpose) if isinstance(purpose, list) else purpose}")
            if good_for:
                content_parts.append(f"권장 피부 타입: {', '.join(good_for) if isinstance(good_for, list) else good_for}")
            if bad_for:
                content_parts.append(f"주의 피부 타입: {', '.join(bad_for) if isinstance(bad_for, list) else bad_for}")
            
            content = "\n".join(content_parts)
            
            doc = Document(
                page_content=content,
                metadata={
                    "ingredient_kor": kor_name,
                    "ingredient_eng": eng_name,
                    "description": (description[:200] + "..." if description and len(description) > 200 else description) or '',
                    "purpose": ', '.join(purpose) if isinstance(purpose, list) else (purpose or ''),
                    "good_for": ', '.join(good_for) if isinstance(good_for, list) else (good_for or ''),
                    "bad_for": ', '.join(bad_for) if isinstance(bad_for, list) else (bad_for or '')
                }
            )
            documents.append(doc)
        
        split_docs = self.text_splitter.split_documents(documents)
        logger.info(f"📄 {len(documents)}개 문서를 {len(split_docs)}개 청크로 분할")
        
        self.vectorstore = Chroma.from_documents(
            documents=split_docs,
            embedding=self.embeddings,
            persist_directory=self.persist_directory
        )
        
        self.retriever = self.vectorstore.as_retriever(search_kwargs={"k": 3})
        logger.info("✅ ChromaDB 벡터 스토어 생성 완료")
    
    def search(self, query: str, top_k: int = 3) -> List[Document]:
        """
        벡터 검색을 수행합니다.
        
        Args:
            query: 검색 쿼리
            top_k: 반환할 최대 결과 개수
        
        Returns:
            검색 결과 Document 리스트
        """
        if self.retriever is None:
            return []
        
        # top_k 동적 변경
        self.retriever.search_kwargs["k"] = top_k
        return self.retriever.invoke(query)

