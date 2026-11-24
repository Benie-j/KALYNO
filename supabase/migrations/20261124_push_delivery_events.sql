create table if not exists public.push_delivery_events (
  id uuid primary key default gen_random_uuid(),
  push_key text,
  topic text not null,
  notification_type text not null,
  title text not null,
  body text not null,
  fcm_status text not null check (fcm_status in ('accepted', 'failed')),
  fcm_message_name text,
  fcm_error text,
  created_at timestamptz not null default now()
);

create index if not exists push_delivery_events_created_at_idx
on public.push_delivery_events(created_at desc);

alter table public.push_delivery_events enable row level security;

grant select on public.push_delivery_events to authenticated;
grant all on public.push_delivery_events to service_role;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public'
      and tablename = 'push_delivery_events'
      and policyname = 'push_delivery_events_admin_select'
  ) then
    create policy "push_delivery_events_admin_select"
    on public.push_delivery_events
    for select to authenticated
    using (public.is_admin());
  end if;
end $$;
