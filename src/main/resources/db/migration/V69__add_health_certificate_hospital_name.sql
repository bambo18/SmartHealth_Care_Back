-- Optional hospital name for manually entered or corrected certificates.
ALTER TABLE public.pet_health_certificates
    ADD COLUMN IF NOT EXISTS hospital_name VARCHAR(255);
