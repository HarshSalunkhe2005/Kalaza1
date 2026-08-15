-- Read-only — run this first and send me back what it prints.
-- I want to see the *actual* current policy before touching it, since I
-- can't query your live DB directly and don't want to guess-and-check
-- against a table this central.

select polname,
       cmd,
       pg_get_expr(polqual, polrelid)      as using_expr,
       pg_get_expr(polwithcheck, polrelid) as with_check_expr
from pg_policy
where polrelid = 'public.patients'::regclass;

-- Also useful: confirm your Super Admin's own staff row actually reads
-- back as SUPER_ADMIN and active (swap in your real staff id or name):
-- select id, name, role, is_active from public.staff where name_lower = 'yourname';
