-- The assistant RAG reads database documents before the packaged JSONL fallback.
-- Keep the two sources aligned with the current pass-based pricing model.
UPDATE assistant_knowledge_documents
SET title = '이용권 요금 · 실전면접 가격',
    content = 'Job-A-Dream AI의 유료 상품은 월 구독이 아니라 실전면접 이용권입니다. 1회 이용권은 1,500원, 5회 이용권은 5,900원, 10회 이용권은 9,900원입니다. 결제하면 해당 횟수만큼 실전면접 이용 횟수가 충전되며, 자동결제·매월 갱신·만료일은 없습니다.'
WHERE scope = 'GLOBAL' AND source_type = 'SERVICE_GUIDE' AND source_id = 'subscription-price';

UPDATE assistant_knowledge_documents
SET title = '이용권 혜택 · 무료 면접',
    content = '모의면접은 모든 회원이 무료로 횟수 제한 없이 이용할 수 있습니다. 이용권은 회원의 역량 프로필 또는 채용공고 요구사항을 바탕으로 RAG와 AI를 활용해 질문을 만드는 실전면접에만 사용됩니다. 일반회원에게는 매달 실전면접 무료 1회가 지급되며, 관리자 계정은 결제 없이 모든 기능을 이용할 수 있습니다.'
WHERE scope = 'GLOBAL' AND source_type = 'SERVICE_GUIDE' AND source_id = 'subscription-benefits';
