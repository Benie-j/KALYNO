
create table if not exists public.student_dojo_attempts (
  client_attempt_id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  flashcard_id text not null,
  attempted_at bigint not null,
  is_success boolean not null,
  attempt_number integer not null default 0,
  rating text not null,
  kind text not null,
  question text not null,
  student_answer text,
  reference_answer text not null,
  lesson_title text not null default '',
  subject_name text not null default '',
  is_legacy boolean not null default false,
  created_at timestamptz not null default now(),
  constraint student_dojo_attempts_attempt_number_nonnegative
    check (attempt_number >= 0)
);

create index if not exists student_dojo_attempts_user_attempted_idx
on public.student_dojo_attempts
(user_id, attempted_at desc, client_attempt_id);

create index if not exists student_dojo_attempts_user_card_idx
on public.student_dojo_attempts
(user_id, flashcard_id, attempted_at desc);

alter table public.student_dojo_attempts
enable row level security;

create policy student_dojo_attempts_select_own
on public.student_dojo_attempts
for select to authenticated
using ((select auth.uid()) = user_id);

create policy student_dojo_attempts_insert_own
on public.student_dojo_attempts
for insert to authenticated
with check ((select auth.uid()) = user_id);

grant select, insert
on public.student_dojo_attempts to authenticated;
