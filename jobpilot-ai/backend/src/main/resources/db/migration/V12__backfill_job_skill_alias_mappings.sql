-- Existing raw job skill rows can be safely connected when their normalized
-- text is an approved alias. Compound requirement sentences remain unmapped.
UPDATE job_skills js
JOIN skills raw_skill ON raw_skill.id = js.skill_id
JOIN skill_aliases alias_entry ON alias_entry.normalized_alias = raw_skill.normalized_name
JOIN skills canonical_skill ON canonical_skill.id = alias_entry.skill_id
SET js.canonical_skill_id = canonical_skill.id
WHERE js.canonical_skill_id IS NULL
  AND canonical_skill.catalog_status = 'CANONICAL';
