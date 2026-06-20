## 1. Data Model and Firestore Mapping

- [x] 1.1 Add a `SpaPackage` model with Firestore-safe default values for catalog and detail fields.
- [x] 1.2 Implement mapping helpers for reading `spa_packages` documents into `SpaPackage` instances.
- [x] 1.3 Define save/update payload fields for name, category, price, original price, duration, image URL, descriptions, benefits, steps, suitability, active state, sort order, and timestamps.

## 2. Admin Spa Package Management

- [x] 2.1 Create `ManageSpaPackageActivity` with Compose screen structure matching existing admin management screens.
- [x] 2.2 Add a realtime Firestore listener for `spa_packages` and render loading, empty, search, and list states.
- [x] 2.3 Add spa package cards with edit, delete, and active/inactive controls.
- [x] 2.4 Add create/edit dialog or form with validation for required package fields.
- [x] 2.5 Reuse Cloudinary image upload flow for spa package images and preserve existing image URLs when editing without replacement.
- [x] 2.6 Save new packages to `spa_packages` and update existing package documents.
- [x] 2.7 Confirm deletion before removing a `spa_packages` document.

## 3. Admin Dashboard Integration

- [x] 3.1 Add spa package counts to `DashboardStats`.
- [x] 3.2 Add a Firestore listener for `spa_packages` in the admin dashboard.
- [x] 3.3 Add a dashboard card that opens `ManageSpaPackageActivity`.
- [x] 3.4 Register `ManageSpaPackageActivity` in `AndroidManifest.xml`.

## 4. Customer Spa Catalog

- [x] 4.1 Add a `SPA` customer navigation state or repurpose the existing booking nav slot into a spa catalog tab.
- [x] 4.2 Load active spa packages from `spa_packages` for the customer-facing catalog.
- [x] 4.3 Render spa package cards with image, name, category, duration, price, and short description.
- [x] 4.4 Add category filtering and search for active spa packages.
- [x] 4.5 Handle loading, empty, and malformed-document states without crashing.

## 5. Customer Spa Detail

- [x] 5.1 Add a spa package detail screen or activity reachable from the customer spa catalog.
- [x] 5.2 Display image, name, category, duration, price, description, benefits, steps, and suitability sections.
- [x] 5.3 Hide optional detail sections when the corresponding list is empty.
- [x] 5.4 Keep appointment booking actions out of scope or present them as non-functional future CTAs.

## 6. Verification

- [x] 6.1 Build the Android project to catch compile errors.
- [ ] 6.2 Manually verify admin can create, edit, search, hide/show, and delete spa packages.
- [ ] 6.3 Manually verify customers only see active spa packages.
- [ ] 6.4 Manually verify package detail screens tolerate missing optional list fields.
- [x] 6.5 Confirm no appointment, consultant assignment, or booking records are created by this change.
