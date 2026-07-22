-- Kalaza Care — comprehensive seed data (mirrors the Firestore seed-data.js
-- used earlier, covering every edge case discussed this session: 24h grace
-- windows, OVERDUE/ADMINISTERED/NOT_ALLOTTED medications, allotment requests,
-- doctor-visit confirm date-gating, approval-queue staleness, an archived
-- patient, and a deactivated utility item with historical data still attached.
--
-- BEFORE RUNNING — create two Auth users (they can't be created by plain SQL,
-- Supabase's Auth needs its own admin API for that):
--   Dashboard → Authentication → Users → Add user
--     1. email: kavita.seed@staff.kalazacare.internal   password: 123456
--        (check "Auto Confirm User")
--     2. email: rahul.seed@staff.kalazacare.internal    password: 123456
--        (check "Auto Confirm User")
--   UUIDs already filled in below (Kavita: 34f51254-..., Rahul: 86083451-...).
--
-- Safe to run once against a fresh schema. Re-running will fail on the
-- unique staff name / patient uniqueness lookups below being skipped (it
-- does not de-duplicate patients/clinical records like the JS version did).

do $$
declare
  kavita_id uuid := '34f51254-5c7e-4515-8d75-285515345f9b';
  rahul_id  uuid := '86083451-aa5f-4f75-89be-19d107f67fbb';

  ramesh_id     uuid := gen_random_uuid();
  sulochana_id  uuid := gen_random_uuid();
  vijay_id      uuid := gen_random_uuid();
  indu_id       uuid := gen_random_uuid();
  shantaram_id  uuid := gen_random_uuid();
  malti_id      uuid := gen_random_uuid();
  ganpat_id     uuid := gen_random_uuid();
  usha_id       uuid := gen_random_uuid();

  mask_id         uuid := gen_random_uuid();
  diaper_pant_id  uuid := gen_random_uuid();
  diaper_stitch_id uuid := gen_random_uuid();
  gloves_id       uuid := gen_random_uuid();
  tina_bed_id     uuid := gen_random_uuid();
  wipes_id        uuid := gen_random_uuid();
  cotton_id       uuid := gen_random_uuid();

  levodopa_med_id uuid := gen_random_uuid();
begin
  -- ── Staff ──────────────────────────────────────────────────────────────
  insert into staff (id, name, email, role, phone, is_active, joined_date, auth_email, fcm_token) values
    (kavita_id, 'Kavita', 'kavita@kalazacare.com', 'STAFF', '+91 98765 00002', true, '2023-06-15', 'kavita.seed@staff.kalazacare.internal', ''),
    (rahul_id,  'Rahul',  'rahul@kalazacare.com',  'SUPERVISOR', '+91 98765 00003', true, '2024-01-10', 'rahul.seed@staff.kalazacare.internal', '');

  -- ── Utility Items (one deactivated after use, historical data preserved) ─
  insert into utility_items (id, name, unit, display_order, is_active) values
    (mask_id, 'Face Mask', 'pcs', 1, true),
    (diaper_pant_id, 'Diaper (Pant)', 'pcs', 2, true),
    (diaper_stitch_id, 'Diaper (Stitch)', 'pcs', 3, true),
    (gloves_id, 'Hand Gloves', 'pairs', 4, true),
    (tina_bed_id, 'Tina Bed', 'pcs', 5, true),
    (wipes_id, 'Wet Wipes', 'pcs', 6, true),
    (cotton_id, 'Cotton Rolls (discontinued)', 'pcs', 7, false);

  -- ── Patients ───────────────────────────────────────────────────────────
  insert into patients (id, name, age, gender, room_no, medical_history, current_issues, allergies, emergency_contact, emergency_phone, admission_date, is_archived, primary_diagnosis) values
    (ramesh_id, 'Ramesh Kulkarni', 78, 'MALE', '101',
      'Diabetes Type 2 (since 2010), Hypertension (since 2015)',
      'Blood sugar fluctuations, mild knee arthritis', 'Penicillin',
      'Suresh Kulkarni (Son)', '9900100001', '2024-02-14', false, 'Diabetes + Hypertension'),
    (sulochana_id, 'Sulochana Bhide', 82, 'FEMALE', '102',
      'Osteoporosis, Mild Dementia (diagnosed 2023)',
      'Confusion episodes in the evening, fall risk', 'None known',
      'Meena Joshi (Daughter)', '9900100002', '2023-11-05', false, 'Dementia + Osteoporosis'),
    (vijay_id, 'Vijay Gokhale', 71, 'MALE', '103',
      'Post-hip replacement surgery, COPD',
      'Physiotherapy ongoing, shortness of breath on exertion', 'Sulfa drugs',
      'Anita Gokhale (Wife)', '9900100003', current_date - 30, false, 'Post-surgical recovery + COPD'),
    (indu_id, 'Indu Apte', 75, 'FEMALE', '104',
      'Parkinson''s disease (Stage 2), Hypothyroidism',
      'Tremors, difficulty swallowing, medication schedule critical', 'Aspirin',
      'Deepak Apte (Husband)', '9900100004', '2025-07-20', false, 'Parkinson''s Disease'),
    (shantaram_id, 'Shantaram Phadke', 88, 'MALE', '201',
      'Chronic heart failure, Atrial fibrillation',
      'Edema in legs, daily weight monitoring required', 'Ibuprofen',
      'Priya Phadke (Daughter)', '9900100005', '2024-08-12', false, 'Chronic Heart Failure'),
    (malti_id, 'Malti Deshpande', 69, 'FEMALE', '202',
      'Stroke recovery, Left-side weakness',
      'Speech therapy, occupational therapy, left arm mobility limited', 'None',
      'Arun Deshpande (Son)', '9900100006', '2026-02-01', false, 'Post-Stroke Recovery'),
    (ganpat_id, 'Ganpat Joshi', 80, 'MALE', '203',
      'Prostate cancer (palliative stage), Chronic pain',
      'Pain management, palliative care, comfort focused', 'Codeine',
      'Mangal Joshi (Son)', '9900100007', '2025-12-03', true, 'Palliative - Prostate Cancer'),
    (usha_id, 'Usha Naik', 73, 'FEMALE', '204',
      'Type 1 Diabetes, Retinopathy',
      'Insulin-dependent, vision impairment, requires assistance with meals', 'Latex',
      'Rohit Naik (Son)', '9900100008', '2025-05-18', false, 'Insulin-Dependent Diabetes');

  -- ── Vitals (24h-grace edge case: one recent, one old) ───────────────────
  insert into vitals (patient_id, date, time, pulse, bp, spo2, temperature, sugar_fasting, sugar_pp, signed_by, created_at) values
    (ramesh_id, current_date, '08:10', '79', '139/89', '97', '98.7', '145', '210', 'Kavita', now()),
    (ramesh_id, current_date - 5, '08:00', '76', '138/88', '97', '98.4', '142', '200', 'Kavita', now() - interval '5 days'),
    (sulochana_id, current_date, '08:00', '74', '128/82', '97', '98.5', '', '', 'Rahul', now());

  -- ── Medications (OVERDUE-on-read, ADMINISTERED, NOT_ALLOTTED, flagged) ──
  insert into medications (patient_id, medicine_name, dose, quantity, schedule_time, scheduled_date, status, administered_by, administered_at, notes, allotment_status, allotted_by_id, allotted_by_name, allotted_at, allotment_photo_url, allotment_photo_expires_at, administered_photo_url, administered_photo_expires_at) values
    (ramesh_id, 'Aspirin', '75mg', '1 tablet', '06:00', current_date, 'PENDING', '', null, '',
      'ALLOTTED', kavita_id, 'Kavita', now() - interval '3 hours', 'https://example.com/seed-evidence/allot1.jpg', now() + interval '45 hours', '', null),
    (ramesh_id, 'Metformin', '500mg', '1 tablet', '08:00', current_date, 'ADMINISTERED', 'Kavita', now() - interval '2 hours',
      '', 'ALLOTTED', kavita_id, 'Kavita', now() - interval '2.25 hours', 'https://example.com/seed-evidence/allot2.jpg', now() + interval '46 hours',
      'https://example.com/seed-evidence/admin1.jpg', now() + interval '46 hours'),
    (vijay_id, 'Tiotropium Inhaler', '1 puff', '1 dose', '20:00', current_date, 'PENDING', '', null, '',
      'NOT_ALLOTTED', null, '', null, '', null, '', null);

  insert into medications (id, patient_id, medicine_name, dose, quantity, schedule_time, scheduled_date, status, administered_by, administered_at, notes, allotment_status, allotted_by_id, allotted_by_name, allotted_at, allotment_photo_url, allotment_photo_expires_at, administered_photo_url, administered_photo_expires_at) values
    (levodopa_med_id, indu_id, 'Levodopa/Carbidopa', '100/25mg', '1 tablet', '12:00', current_date, 'PENDING', '', null,
      'Give with food to reduce nausea', 'NOT_ALLOTTED', null, '', null, '', null, '', null);

  -- ── Allotment Request (flagged by staff, awaiting Supervisor) ───────────
  insert into allotment_requests (medication_entry_id, patient_id, patient_name, medicine_name, dose, scheduled_time, requested_by_id, requested_by_name, status, fulfilled_by_id, fulfilled_by_name, timestamp, fulfilled_at) values
    (levodopa_med_id, indu_id, 'Indu Apte', 'Levodopa/Carbidopa', '100/25mg', '12:00', kavita_id, 'Kavita', 'PENDING', null, '', now(), null);

  -- ── Utility Records (incl. a deactivated item still logged historically) ─
  insert into utility_records (patient_id, date, time, quantities, issued_to_caregiver, issued_by_supervisor, checked_by, created_at) values
    (ramesh_id, current_date, '07:00',
      jsonb_build_object(mask_id::text, 2, diaper_pant_id::text, 3, cotton_id::text, 5),
      'Kavita', 'Rahul', 'Somnath', now()),
    (sulochana_id, current_date - 3, '07:00',
      jsonb_build_object(wipes_id::text, 10, gloves_id::text, 2),
      'Rahul', 'Somnath', '', now() - interval '3 days');

  -- ── Doctor Visits (future unconfirmed, past unconfirmed, past confirmed+archived) ─
  insert into doctor_visits (patient_id, doctor_name, specialty, date, time, notes, next_visit_date, prescription_changes, is_confirmed, is_archived) values
    (ramesh_id, 'Dr. Anita Joshi', 'Cardiologist', current_date + 30, '10:00', '', null, '', false, false),
    (vijay_id, 'Dr. Rajesh Nair', 'Orthopedic Surgeon', current_date - 2, '11:00', 'Physio progressing well.', current_date + 20, 'No changes', false, false),
    (ramesh_id, 'Dr. Suresh Mehta', 'Endocrinologist', current_date - 14, '09:30', 'HbA1c improved to 7.2. Continue current Metformin dose.', current_date + 76, 'No changes', true, true);

  -- ── Care Notes (one recent, one old) ────────────────────────────────────
  insert into care_notes (patient_id, staff_id, staff_name, timestamp, note) values
    (ramesh_id, kavita_id, 'Kavita', now() - interval '3 hours',
      'Patient had a good morning. Ate full breakfast. Complained of slight knee pain — applied cold pack.'),
    (sulochana_id, rahul_id, 'Rahul', now() - interval '30 hours',
      'Mild confusion around 6 PM. Redirected gently with familiar photos. Settled within 20 minutes.');

  -- ── Approval Requests (one still valid, one already stale) ─────────────
  insert into approval_requests (entity_type, entity_id, action, patient_id, patient_name, requested_by_id, requested_by_name, field_changed, old_value, new_value, status, reviewed_by_id, reviewed_by_name, timestamp, reviewed_at, rejection_reason) values
    ('PATIENT', '', 'EDIT', sulochana_id, 'Sulochana Bhide', kavita_id, 'Kavita', 'Current Issues',
      'Confusion episodes in the evening, fall risk',
      'Confusion episodes throughout the day, high fall risk - bed rails requested',
      'PENDING', null, '', now() - interval '2 hours', null, ''),
    ('PATIENT', '', 'EDIT', vijay_id, 'Vijay Gokhale', rahul_id, 'Rahul', 'Allergies',
      'Sulfa drugs, Latex (already outdated on purpose)',
      'Sulfa drugs, Latex, Penicillin',
      'PENDING', null, '', now() - interval '5 hours', null, '');

  -- ── Audit Log ────────────────────────────────────────────────────────────
  insert into audit_log (action, performed_by_id, performed_by_name, target_patient_id, target_patient_name, details, timestamp, icon_name) values
    ('Medication Administered', kavita_id, 'Kavita', ramesh_id::text, 'Ramesh Kulkarni', 'Metformin 500mg administered', now() - interval '2 hours', 'medication'),
    ('Vitals Recorded', kavita_id, 'Kavita', ramesh_id::text, 'Ramesh Kulkarni', 'BP: 139/89, Pulse: 79, SPO2: 97%, Temp: 98.7°F', now() - interval '2.2 hours', 'monitor_heart');

  -- ── Notifications (targeted + broadcast, read + unread) ────────────────
  insert into notifications (recipient_staff_id, recipient_role, type, title, message, timestamp, is_read, target_route) values
    (null, 'SUPER_ADMIN', 'APPROVAL_REQUESTED', 'New Edit Request', 'Kavita requested a change to Sulochana Bhide''s Current Issues', now() - interval '2 hours', false, 'approval'),
    (null, 'SUPERVISOR', 'ALLOTMENT_REQUESTED', 'Allotment Needed', 'Kavita flagged Levodopa/Carbidopa for Indu Apte as not yet allotted', now(), false, 'medicine'),
    (kavita_id, null, 'APPROVAL_APPROVED', 'Edit Request Approved', 'Your Emergency Phone change for Ramesh Kulkarni was approved', now() - interval '18 hours', true, 'patient/' || ramesh_id::text);

  raise notice 'Seed complete. New staff logins: Kavita / 123456 (Staff), Rahul / 123456 (Supervisor)';
end $$;
