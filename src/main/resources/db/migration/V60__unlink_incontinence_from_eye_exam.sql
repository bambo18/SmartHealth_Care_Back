-- Remove incorrect eye examination association
-- for canine incontinence.
-- Keep the original condition and translations.

DELETE FROM condition_check_methods ccm
USING conditions c, check_methods cm
WHERE ccm.condition_id = c.id
  AND ccm.check_method_id = cm.id
  AND c.name = 'Incontinence'
  AND c.species = 'DOG'
  AND cm.name = 'Eye Examination';