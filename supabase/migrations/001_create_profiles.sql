-- GRID IPTV cloud account profile (run in Supabase SQL editor for project wvdtnmoqbhvmrbgrhcpw)

create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    email text,
    display_name text,
    avatar_url text,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

alter table public.profiles enable row level security;

create policy "Profiles are readable by owner"
    on public.profiles for select
    using (auth.uid() = id);

create policy "Profiles are insertable by owner"
    on public.profiles for insert
    with check (auth.uid() = id);

create policy "Profiles are updatable by owner"
    on public.profiles for update
    using (auth.uid() = id);

create or replace function public.set_profiles_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = timezone('utc', now());
    return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
    before update on public.profiles
    for each row execute function public.set_profiles_updated_at();
