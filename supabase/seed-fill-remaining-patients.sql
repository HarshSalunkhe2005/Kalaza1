-- Fills in Vitals/Medications/Utility/Doctor Visits/Care Notes for the 4
-- patients the original seed.sql left with zero clinical records (Shantaram
-- Phadke, Malti Deshpande, Ganpat Joshi, Usha Naik) — everything else in
-- seed.sql already covers Ramesh/Sulochana/Vijay/Indu. Looks patients/staff/
-- utility_items up by name rather than re-inserting them, so it's safe to run
-- once against the already-seeded database.

do $$
declare
  somnath_id uuid;
  arti_id uuid;
  shantaram_id uuid;
  malti_id uuid;
  ganpat_id uuid;
  usha_id uuid;
  mask_id uuid;
  diaper_pant_id uuid;
  gloves_id uuid;
  wipes_id uuid;
begin
  select id into somnath_id from staff where name = 'Somnath' limit 1;
  select id into arti_id from staff where name = 'Arti' limit 1;
  select id into shantaram_id from patients where name = 'Shantaram Phadke' limit 1;
  select id into malti_id from patients where name = 'Malti Deshpande' limit 1;
  select id into ganpat_id from patients where name = 'Ganpat Joshi' limit 1;
  select id into usha_id from patients where name = 'Usha Naik' limit 1;
  select id into mask_id from utility_items where name = 'Face Mask' limit 1;
  select id into diaper_pant_id from utility_items where name = 'Diaper (Pant)' limit 1;
  select id into gloves_id from utility_items where name = 'Hand Gloves' limit 1;
  select id into wipes_id from utility_items where name = 'Wet Wipes' limit 1;

  if shantaram_id is null or malti_id is null or ganpat_id is null or usha_id is null then
    raise exception 'One or more patients not found by name — check seed.sql ran first.';
  end if;

  -- ── Vitals ───────────────────────────────────────────────────────────────
  insert into vitals (patient_id, date, time, pulse, bp, spo2, temperature, sugar_fasting, sugar_pp, signed_by) values
    (shantaram_id, current_date, '07:30', '88', '132/84', '94', '98.2', '', '', 'Somnath'),
    (malti_id, current_date, '08:15', '76', '126/80', '97', '98.6', '', '', 'Arti'),
    (ganpat_id, current_date - 1, '09:00', '70', '108/68', '95', '97.9', '', '', 'Somnath'),
    (usha_id, current_date, '07:45', '82', '130/85', '98', '98.4', '158', '220', 'Arti');

  -- ── Medications ──────────────────────────────────────────────────────────
  insert into medications (patient_id, medicine_name, dose, quantity, schedule_time, scheduled_date, status, administered_by, administered_at, notes, allotment_status, allotted_by_id, allotted_by_name, allotted_at) values
    (shantaram_id, 'Furosemide', '40mg', '1 tablet', '08:00', current_date, 'ADMINISTERED', 'Somnath', now() - interval '1 hour', 'Monitor daily weight', 'ALLOTTED', somnath_id, 'Somnath', now() - interval '2 hours'),
    (malti_id, 'Atorvastatin', '20mg', '1 tablet', '21:00', current_date, 'PENDING', '', null, '', 'NOT_ALLOTTED', null, '', null),
    (ganpat_id, 'Morphine Sulfate', '10mg', '1 dose', '10:00', current_date, 'ADMINISTERED', 'Arti', now() - interval '3 hours', 'Palliative pain management', 'ALLOTTED', arti_id, 'Arti', now() - interval '3.5 hours'),
    (usha_id, 'Insulin Glargine', '20 units', '1 injection', '07:00', current_date, 'ADMINISTERED', 'Arti', now() - interval '1 hour', 'Given with breakfast', 'ALLOTTED', arti_id, 'Arti', now() - interval '1.25 hours');

  -- ── Utility Records ──────────────────────────────────────────────────────
  insert into utility_records (patient_id, date, time, quantities, issued_to_caregiver, issued_by_supervisor, checked_by) values
    (shantaram_id, current_date, '07:00', jsonb_build_object(mask_id::text, 1, gloves_id::text, 1), 'Somnath', 'Arti', 'Somnath'),
    (malti_id, current_date, '07:15', jsonb_build_object(diaper_pant_id::text, 2, wipes_id::text, 8), 'Arti', 'Somnath', ''),
    (ganpat_id, current_date - 1, '07:30', jsonb_build_object(diaper_pant_id::text, 2, mask_id::text, 1), 'Somnath', 'Arti', 'Somnath'),
    (usha_id, current_date, '07:45', jsonb_build_object(gloves_id::text, 1, wipes_id::text, 6), 'Arti', 'Somnath', 'Arti');

  -- ── Doctor Visits ────────────────────────────────────────────────────────
  insert into doctor_visits (patient_id, doctor_name, specialty, date, time, notes, next_visit_date, prescription_changes, is_confirmed, is_archived) values
    (shantaram_id, 'Dr. Meera Kulkarni', 'Cardiologist', current_date - 5, '10:00', 'Edema improving with diuretic. Continue current regimen.', current_date + 25, 'No changes', true, false),
    (malti_id, 'Dr. Sanjay Rao', 'Neurologist', current_date + 10, '11:30', '', null, '', false, false),
    (ganpat_id, 'Dr. Anjali Deshmukh', 'Palliative Care', current_date - 3, '15:00', 'Comfort measures reviewed with family. Pain well controlled.', current_date + 27, 'Increased morphine dose slightly', true, false),
    (usha_id, 'Dr. Vikram Shah', 'Endocrinologist', current_date - 20, '09:00', 'HbA1c stable. Continue insulin regimen.', current_date + 70, 'No changes', true, true);

  -- ── Care Notes ───────────────────────────────────────────────────────────
  insert into care_notes (patient_id, staff_id, staff_name, timestamp, note) values
    (shantaram_id, somnath_id, 'Somnath', now() - interval '4 hours', 'Legs elevated during rest, mild edema noted. Weight recorded and logged.'),
    (malti_id, arti_id, 'Arti', now() - interval '6 hours', 'Participated well in speech therapy session. Mood positive.'),
    (ganpat_id, somnath_id, 'Somnath', now() - interval '2 hours', 'Resting comfortably. Family visited in the afternoon.'),
    (usha_id, arti_id, 'Arti', now() - interval '5 hours', 'Assisted with breakfast due to vision impairment. Insulin administered on schedule.');

  raise notice 'Filled clinical records for Shantaram, Malti, Ganpat, and Usha.';
end $$;
