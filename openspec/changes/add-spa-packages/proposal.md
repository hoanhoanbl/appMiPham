## Why

The app currently focuses on cosmetics shopping and does not have spa package content for customers to browse or for admins to manage. Adding spa packages first creates the content foundation needed before introducing appointment booking and consultant workflows later.

## What Changes

- Add a new spa package catalog capability backed by Firestore.
- Allow admins to create, edit, search, hide/show, and manage spa packages from the admin area.
- Show active spa packages to customers in the user-facing product/home experience.
- Add a spa package detail experience with service information, pricing, duration, benefits, and process details.
- Keep appointment booking, consultant assignment, and schedule management out of scope for this change.

## Capabilities

### New Capabilities
- `spa-packages`: Customer and admin management of spa package catalog content.

### Modified Capabilities

## Impact

- Firestore: add a new `spa_packages` collection.
- Android UI: add customer-facing spa package list/detail screens or tabs.
- Android admin UI: add spa package management screen and admin dashboard entry.
- Android models: add spa package data model and mapping logic.
- Navigation/manifest: register any new activities needed for spa package management and details.
