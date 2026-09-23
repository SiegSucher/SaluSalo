# Salu-Salo Supabase setup

This folder contains the Phase 1 database and Storage foundation.

## Apply the database migration

In the Supabase dashboard for qzctlsyaksurvrykzhwf:

1. Go to SQL Editor.
2. Open migrations/20260921_phase1_foundation.sql.
3. Run the entire file.
4. Confirm that public.users, food-images, and profile-images exist.

The migration enables RLS and gives no access to anonymous clients. It does not
grant users any way to create an ADMIN profile.

## Required Firebase third-party authentication setup

Before the Android app can read or create user profiles:

1. In Supabase, open Authentication > Third-Party Auth.
2. Add a Firebase Auth integration with Firebase project ID salusalo.
3. In Firebase, use a trusted Admin SDK / Cloud Function to put
   role: authenticated into the ID token for every user.
4. Existing Firebase users must receive that same custom claim and sign in again
   or refresh their ID token.

Never put a Firebase Admin credential or Supabase service-role key in this app.