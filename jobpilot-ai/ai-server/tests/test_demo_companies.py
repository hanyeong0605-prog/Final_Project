from collections import Counter


def test_demo_bundle_is_deterministic_and_referentially_complete():
    from ml.sentiment.generate_demo import generate_bundle
    bundle = generate_bundle()
    assert bundle == generate_bundle()
    assert len(bundle['companies']) == 100
    assert len(bundle['postings']) == 100
    assert len(bundle['reviews']) == 500
    companies = {c['seed_key'] for c in bundle['companies']}
    assert len(companies) == 100
    postings = {p['seed_key']: p for p in bundle['postings']}
    assert len(postings) == 100
    assert all('(가상기업)' in c['name'] for c in bundle['companies'])
    assert all(p['source_provider'] == 'FICTIONAL_DEMO' for p in postings.values())
    assert all(p['company_seed_key'] in companies for p in postings.values())
    assert set(Counter(r['company_seed_key'] for r in bundle['reviews']).values()) == {5}
    assert len({r['seed_key'] for r in bundle['reviews']}) == 500
    for review in bundle['reviews']:
        assert postings[review['posting_seed_key']]['company_seed_key'] == review['company_seed_key']
        assert 1 <= review['rating'] <= 5
        assert review['source_type'] == 'SYNTHETIC_DEMO'
        assert review['analysis_state'] == 'PENDING'
        assert 'sentiment' not in review


def test_postings_have_job_sections_and_no_real_company_links():
    from ml.sentiment.generate_demo import generate_bundle
    for post in generate_bundle()['postings']:
        for section in ['주요 업무', '자격 요건', '우대 사항', '복리후생', '가상']:
            assert section in post['description']
        assert post['company_url'] is None
        assert post['source_url'] is None
        assert post['employer_account_id'] is None


def test_review_text_does_not_repeat_pros_and_cons_in_body():
    for review in __import__('ml.sentiment.generate_demo', fromlist=['generate_bundle']).generate_bundle()['reviews']:
        assert review['pros'] not in review['body']
        assert review['cons'] not in review['body']
