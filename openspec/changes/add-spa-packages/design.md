## Context

The Android app is a Kotlin Jetpack Compose cosmetics shopping app backed primarily by Firebase Auth and Firestore. Existing customer catalog data is read from Firestore collections such as `products` and `brands`, while admin management screens use direct Firestore listeners and updates. Product images currently use a reusable `CloudinaryHelper` upload/delete flow and store the resulting URL in Firestore.

There is no current spa package data model, admin management UI, or customer-facing spa catalog. The customer bottom navigation already contains a booking-related entry that currently routes to the orders tab, so this change can repurpose that area into a spa package browsing surface before appointment booking exists.

## Goals / Non-Goals

**Goals:**
- Introduce a Firestore-backed spa package catalog using a new `spa_packages` collection.
- Provide an admin screen for creating, editing, searching, hiding/showing, and deleting spa packages.
- Add an admin dashboard entry and basic package counts.
- Add a customer-facing spa package list and detail experience.
- Preserve the current app style: Compose screens, mint theme, rounded cards, Firestore realtime listeners, and Cloudinary image URLs.
- Store enough package metadata to support a later appointment-booking phase without schema churn.

**Non-Goals:**
- Do not create appointment booking, schedule management, consultant assignment, or appointment status workflows.
- Do not introduce consultant role navigation in this change.
- Do not add payment for spa packages.
- Do not migrate existing products into spa packages.
- Do not introduce a new backend service or dependency.

## Decisions

### Use a separate `spa_packages` Firestore collection

Spa packages are service catalog records, not shippable products. They need duration, benefits, steps, and service suitability fields rather than stock, brand, cart, or checkout data. A separate collection avoids overloading `products` and keeps the future appointment flow clean.

Alternative considered: reuse `products` with a service category. This would make the customer list easy to reuse but would create awkward fields such as stock and brand for service packages, and would couple spa services to cart/checkout behavior.

### Store package images as `imageUrl` and reuse Cloudinary

The current product admin flow uploads images to Cloudinary and stores URL strings in Firestore. Spa package management should follow the same pattern so implementation matches the existing project and does not require Firebase Storage changes.

Alternative considered: manual URL-only input. This is simpler but less consistent with product management and worse for admin usability.

### Repurpose the existing booking nav slot into a spa catalog entry

The customer bottom navigation already has a booking-related item that currently selects the orders tab. This change should make that item a real spa package tab or route, with copy such as "Spa". Appointment booking can later build from the package detail CTA.

Alternative considered: add a fifth bottom-nav item. That increases visual density and keeps the broken booking item around.

### Keep user-visible packages filtered by `isActive`

Admin-created packages should be hidden from users unless `isActive == true`. This mirrors the existing product visibility behavior (`isHidden`) while using positive naming that fits service availability.

Alternative considered: use `isHidden`. That matches products but reads less naturally for services and future scheduling.

### Use string arrays for package detail sections

Fields such as `benefits`, `steps`, and `suitableFor` should be stored as lists of strings. The admin form can edit them as newline-separated text and map them to lists at save time.

Alternative considered: store these sections as one rich text blob. That makes rendering and validation less structured and less useful for future package detail UI.

## Risks / Trade-offs

- Firestore documents may have missing fields during early manual seeding -> Use safe defaults when mapping snapshots.
- Cloudinary helper currently uploads to a hardcoded `products` folder -> Reusing it keeps scope small, but a future improvement could make the folder configurable.
- Long admin forms can become cumbersome on mobile -> Use compact sections and multiline fields for list-like content.
- Firestore queries ordered by `sortOrder` and `createdAt` may need composite indexes if combined with filters -> Start with simple collection listener and filter/sort client-side for the expected small catalog size.
- Without appointment booking, customers may expect an immediate action -> Use a non-booking CTA such as "View details" or keep "Book appointment" visually disabled until the booking change is implemented.

## Migration Plan

1. Add the new app code paths and Firestore mapping with safe defaults.
2. Admins can create initial spa packages through the new admin screen after deployment.
3. Existing users and products are unaffected because the change uses a new collection.
4. Rollback can remove the UI entry and ignore `spa_packages` data without impacting existing ecommerce flows.
