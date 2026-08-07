import pymysql
from kiwipiepy import Kiwi
from gensim.models import Word2Vec

# 1. Kiwi 형태소 분석기 및 불용어 정의
kiwi = Kiwi()
STOPWORDS = {
    "채용", "우대", "경력", "신입", "가능자", "관련", "업무", "자격", "요건",
    "성남시", "분당구", "서울특별시", "강남구", "구로구", "판교", "위치"
}


def fetch_job_requirements() -> list[str]:
    """jobpilot DB의 job_requirements 테이블에서 content와 source_excerpt를 가져옵니다."""
    conn = pymysql.connect(
        host="localhost",
        port=3306,
        user="root",
        password="your_password",  # 👈 로컬 DB 비밀번호에 맞게 수정
        db="jobpilot",
        charset="utf8mb4"
    )
    try:
        with conn.cursor() as cursor:
            sql = "SELECT content, source_excerpt FROM job_requirements"
            cursor.execute(sql)
            rows = cursor.fetchall()

            texts = []
            for row in rows:
                content = row[0] or ""
                excerpt = row[1] or ""
                combined = f"{content} {excerpt}".strip()
                if combined:
                    texts.append(combined)
            return texts
    finally:
        conn.close()


def preprocess_for_word2vec(texts: list[str]) -> list[list[str]]:
    """
    Word2Vec 학습을 위해 각 공고 문장을
    단어(명사) 리스트 형태의 말뭉치(Corpus)로 변환합니다.
    예: [['React', 'TypeScript', 'Docker'], ['Java', 'Spring', 'JPA']]
    """
    corpus = []
    for text in texts:
        tokens = kiwi.tokenize(text)
        # 명사(N)만 추출 및 불용어/1글자 제거
        keywords = [
            token.form for token in tokens
            if token.tag.startswith("N") and token.form not in STOPWORDS and len(token.form) > 1
        ]
        if keywords:
            corpus.append(keywords)
    return corpus


def train_model():
    """Word2Vec 모델을 학습하고 파일로 저장합니다."""
    # 1. DB 데이터 조회
    texts = fetch_job_requirements()
    if not texts:
        print("학습에 사용할 DB 데이터가 없습니다.")
        return

    # 2. 자연어 전처리 (말뭉치 생성)
    corpus = preprocess_for_word2vec(texts)

    # 3. Word2Vec 모델 학습
    # - vector_size: 단어를 표현할 벡터 차원 수
    # - window: 문맥 탐색 범위
    # - min_count: 최소 출현 빈도 (2번 이상 등장한 기술 스택만 학습)
    # - sg=1: Skip-gram 방식 적용 (희귀한 연관 기술 스택 찾기에 더 유용)
    model = Word2Vec(
        sentences=corpus,
        vector_size=100,
        window=5,
        min_count=2,
        workers=4,
        sg=1
    )

    # 4. 학습된 모델 저장
    model_path = "word2vec_skills.model"
    model.save(model_path)
    print(f"✅ Word2Vec 모델 학습 완료! ({len(corpus)}개 공고 데이터 학습 완료 -> {model_path} 저장됨)")


if __name__ == "__main__":
    train_model()