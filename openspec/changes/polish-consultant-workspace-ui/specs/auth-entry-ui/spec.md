## ADDED Requirements

### Requirement: Login screen presents a polished spa entry experience
The system SHALL present the login screen with a polished spa-aligned visual hierarchy while preserving the existing authentication flow.

#### Scenario: Visitor opens login
- **WHEN** a signed-out visitor opens the login screen
- **THEN** the system shows a refined brand header, email and password fields, primary login action, home continuation action, and register entry point
- **AND** all visible copy is readable Vietnamese without encoding artifacts

#### Scenario: Login is in progress
- **WHEN** the user submits credentials and authentication is running
- **THEN** the system disables duplicate login actions and shows a clear loading state

#### Scenario: Login fails
- **WHEN** Firebase authentication or role lookup fails
- **THEN** the system shows a clear error message in the login form without disrupting the entered email and password state

### Requirement: Login preserves role-aware navigation
The system MUST preserve existing role-based navigation and spa booking redirects after successful login.

#### Scenario: Customer logs in from spa booking redirect
- **WHEN** a customer logs in with a pending spa package booking redirect
- **THEN** the system opens the spa appointment booking screen for the requested package

#### Scenario: Staff role logs in
- **WHEN** an admin or consultant logs in successfully
- **THEN** the system routes the user to the existing admin dashboard or consultant dashboard according to the stored role

#### Scenario: Customer role logs in normally
- **WHEN** a customer logs in without a spa booking redirect
- **THEN** the system routes the user to the product home screen
