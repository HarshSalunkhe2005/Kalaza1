-- Completes clinical record coverage so every one of the 8 seeded patients has
-- at least one row in every clinical table (Vitals, Medications, Utility
-- Records, Doctor Visits, Care Notes). After seed.sql + seed-fill-remaining-
-- patients.sql, Ramesh already has all 5; this fills the specific gaps left
-- in Sulochana (medications, doctor_visits), Vijay (vitals, utility_records,
-- care_notes), and Indu (vitals, utility_records, doctor_visits, care_notes).
-- Looks everything up by name, so it's safe to run once after the other two
-- seed scripts.

do $$
declare
  somnath_id uuid;
  arti_id uuid;
  sulochana_id uuid;
  vijay_id uuid;
  indu_id uuid;
  mask_id uuid;
  tina_bed_id uuid;
  diaper_stitch_id uuid;
begin
  select id into somnath_id from staff where name = 'Somnath' limit 1;
  select id into arti_id from staff where name = 'Arti' limit 1;
  select id into sulochana_id from patients where name = 'Sulochana Bhide' limit 1;
  select id into vijay_id from patients where name = 'Vijay Gokhale' limit 1;
  select id into indu_id from patients where name = 'Indu Apte' limit 1;
  select id into mask_id from utility_items where name = 'Face Mask' limit 1;
  select id into tina_bed_id from utility_items where name = 'Tina Bed' limit 1;
  select id into diaper_stitch_id from utility_items where name = 'Diaper (Stitch)' limit 1;

  if sulochana_id is null or vijay_id is null or indu_id is null then
    raise exception 'One or more patients not found by name — check seed.sql ran first.';
  end if;

  -- ── Sulochana Bhide: medications + doctor visit ─────────────────────────
  insert into medications (patient_id, medicine_name, dose, quantity, schedule_time, scheduled_date, status, administered_by, administered_at, notes, allotment_status, allotted_by_id, allotted_by_name, allotted_at) values
    (sulochana_id, 'Donepezil', '5mg', '1 tablet', '20:00', current_date, 'ADMINISTERED', 'Arti', now() - interval '1 hour', 'For dementia management', 'ALLOTTED', arti_id, 'Arti', now() - interval '1.5 hours'),
    (sulochana_id, 'Calcium + Vitamin D3', '500mg', '1 tablet', '09:00', current_date, 'ADMINISTERED', 'Somnath', now() - interval '4 hours', 'For osteoporosis', 'ALLOTTED', somnath_id, 'Somnath', now() - interval '4.5 hours');
  insert into doctor_visits (patient_id, doctor_name, specialty, date, time, notes, next_visit_date, prescription_changes, is_confirmed, is_archived) values
    (sulochana_id, 'Dr. Neha Kelkar', 'Neurologist', current_date - 10, '10:30', 'Evening confusion episodes noted, discussed with family. Continue Donepezil.', current_date + 50, 'No changes', true, false);

  -- ── Vijay Gokhale: vitals + utility record + care note ──────────────────
  insert into vitals (patient_id, date, time, pulse, bp, spo2, temperature, sugar_fasting, sugar_pp, signed_by) values
    (vijay_id, current_date, '08:00', '84', '128/82', '92', '98.3', '', '', 'Somnath');
  insert into utility_records (patient_id, date, time, quantities, issued_to_caregiver, issued_by_supervisor, checked_by) values
    (vijay_id, current_date, '07:30', jsonb_build_object(tina_bed_id::text, 1, mask_id::text, 1), 'Somnath', 'Arti', 'Somnath');
  insert into care_notes (patient_id, staff_id, staff_name, timestamp, note) values
    (vijay_id, somnath_id, 'Somnath', now() - interval '3 hours', 'Physiotherapy session completed. Slight shortness of breath on exertion, resting comfortably now.');

  -- ── Indu Apte: vitals + utility record + doctor visit + care note ───────
  insert into vitals (patient_id, date, time, pulse, bp, spo2, temperature, sugar_fasting, sugar_pp, signed_by) values
    (indu_id, current_date, '08:30', '78', '122/78', '97', '98.5', '', '', 'Arti');
  insert into utility_records (patient_id, date, time, quantities, issued_to_caregiver, issued_by_supervisor, checked_by) values
    (indu_id, current_date, '07:45', jsonb_build_object(diaper_stitch_id::text, 2, mask_id::text, 1), 'Arti', 'Somnath', 'Arti');
  insert into doctor_visits (patient_id, doctor_name, specialty, date, time, notes, next_visit_date, prescription_changes, is_confirmed, is_archived) values
    (indu_id, 'Dr. Prakash Iyer', 'Neurologist', current_date - 7, '14:00', 'Tremors stable on current Levodopa dose. Swallowing difficulty being monitored.', current_date + 53, 'No changes', true, false);
  insert into care_notes (patient_id, staff_id, staff_name, timestamp, note) values
    (indu_id, arti_id, 'Arti', now() - interval '2 hours', 'Assisted with meal due to swallowing difficulty. Tremors noticeable but manageable today.');

  raise notice 'Filled remaining clinical record gaps for Sulochana, Vijay, and Indu.';
end $$;
