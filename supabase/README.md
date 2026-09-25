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
## Phase 2: food listings

Run `migrations/20260923_phase2_food_posts.sql` in the Supabase SQL Editor after
Phase 1. It creates `food_posts`, enables RLS, limits writes to the logged-in
Firebase user's matching profile, and updates the existing private `food-images`
bucket policies. Images remain private; the app creates short-lived signed URLs
for authenticated users to view listing images.

## Phase 3: reservations

Run `migrations/20260924_phase3_reservations.sql` after Phases 1 and 2. It creates the `reservations` table and the `create_reservation`, `cancel_reservation`, and `claim_reservation` RPCs. Do not create reservations by inserting rows from the client: the RPCs lock the food post and change quantity atomically.

Run migrations/20260924_phase3_donor_recipient_names.sql after Phase 3 if Phase 3 was already applied. It securely exposes the recipient name only to the donor of that reservation's food post.

## Phase 4: messaging and notifications

Run `migrations/20260926_phase4_messaging_notifications.sql` after Phases 1–3 (including the Phase 3 donor-names patch). It creates `conversations`, `messages`, `notifications` and `notification_preferences`, the messaging RPCs (`start_listing_conversation`, `open_reservation_conversation`, `send_message`, `mark_conversation_read`, `mark_notifications_read`, `get_my_conversations`) and a trigger on `reservations` that creates reservation alerts. It also adds `messages` and `notifications` to the `supabase_realtime` publication; check that both appear under Database → Publications → `supabase_realtime` afterwards.

Clients can only read conversations, messages and alerts. Messages are sent only through `send_message`, and alerts are created only by the database (`notify_user` is not callable by clients). Participants see each other's display name only, through `get_my_conversations`; `users` RLS is unchanged. Deleting a listing also deletes its conversations, messages and alerts. The migration is safe to run again.
