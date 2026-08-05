import time
from app.domain.interview.question_generator import generate_question

for i in range(8):
    start = time.time()
    q = generate_question()
    elapsed = time.time() - start
    print(f"[{i+1}] ({elapsed:.1f}초) {q}")