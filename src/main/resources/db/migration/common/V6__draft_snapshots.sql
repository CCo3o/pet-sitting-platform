ALTER TABLE booking_drafts ADD COLUMN service_snapshot VARCHAR(1200) NOT NULL DEFAULT '';
ALTER TABLE booking_drafts ADD COLUMN pet_snapshot VARCHAR(1200) NOT NULL DEFAULT '';
ALTER TABLE booking_drafts ADD COLUMN address_snapshot VARCHAR(500) NOT NULL DEFAULT '';
