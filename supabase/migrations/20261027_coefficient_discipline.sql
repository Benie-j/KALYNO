-- Coefficient d'une discipline, saisi dans l'espace admin et lu par l'application (moteur FSRS).
-- Sans danger si la colonne existe déjà.
alter table public.subjects add column if not exists coefficient numeric not null default 1;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'subjects_coefficient_positive') then
    alter table public.subjects
      add constraint subjects_coefficient_positive check (coefficient > 0 and coefficient <= 20) not valid;
  end if;
end $$;
