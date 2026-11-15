ALTER TABLE public.pet_health_certificates
    ALTER COLUMN disease_name DROP NOT NULL,
    ALTER COLUMN diagnosed_date_text DROP NOT NULL;
