ALTER TABLE urine_measurements
ADD CONSTRAINT chk_color_r CHECK (color_r >= 0 AND color_r <= 255);

ALTER TABLE urine_measurements
ADD CONSTRAINT chk_color_g CHECK (color_g >= 0 AND color_g <= 255);

ALTER TABLE urine_measurements
ADD CONSTRAINT chk_color_b CHECK (color_b >= 0 AND color_b <= 255);
