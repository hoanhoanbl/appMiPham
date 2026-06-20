## ADDED Requirements

### Requirement: Consultant role is represented in user profiles
The system SHALL treat `users/{uid}.role = 2` as the consultant role while preserving `0 = customer` and `1 = admin`.

#### Scenario: User profile has consultant role
- **WHEN** the system reads a signed-in user's Firestore profile and the role value is `2`
- **THEN** the system identifies that user as a consultant

#### Scenario: New registration remains customer
- **WHEN** a new user registers through the customer registration flow
- **THEN** the system creates the user profile with `role = 0`

### Requirement: Login routes consultants to consultant dashboard
The system SHALL route signed-in consultants to the consultant dashboard after login.

#### Scenario: Consultant logs in
- **WHEN** a user signs in successfully and their Firestore profile has `role = 2`
- **THEN** the system navigates to the consultant dashboard instead of the customer product screen or admin dashboard

#### Scenario: Admin and customer routing remains unchanged
- **WHEN** a user signs in successfully with `role = 1` or any non-consultant customer role
- **THEN** the system routes admins to the admin dashboard and customers to the customer product screen

### Requirement: Existing signed-in consultant routes correctly
The system SHALL route an already-authenticated consultant to the consultant dashboard when the app checks the current Firebase Auth user.

#### Scenario: Consultant opens app while already signed in
- **WHEN** the app starts or login screen opens and Firebase Auth already has a current user with `role = 2`
- **THEN** the system routes that user to the consultant dashboard

#### Scenario: Role lookup fails for existing session
- **WHEN** role lookup fails for an already-authenticated session
- **THEN** the system falls back to customer routing without granting consultant or admin access

### Requirement: Consultant workspace is consultant-only
The system SHALL restrict consultant dashboard actions to users identified as consultants.

#### Scenario: Customer attempts consultant workspace access
- **WHEN** a signed-in customer without `role = 2` attempts to open the consultant dashboard
- **THEN** the system prevents consultant-only actions and routes or reports access denial

#### Scenario: Consultant performs consultant action
- **WHEN** a signed-in consultant opens the consultant dashboard
- **THEN** the system allows pending appointment review and confirmation actions

### Requirement: Role labels are clear in app logic
The system SHALL keep role handling understandable by documenting or centralizing the role meanings used by auth and appointment workflows.

#### Scenario: Developer reads role handling
- **WHEN** a developer inspects role-based routing or user creation code
- **THEN** the role meanings for customer, admin, and consultant are visible in code comments or constants
