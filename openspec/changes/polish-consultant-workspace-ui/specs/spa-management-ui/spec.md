## ADDED Requirements

### Requirement: Admin appointment management is scannable
The system SHALL present admin appointment management as a scannable operational view with summaries, filters, search, and clear appointment actions.

#### Scenario: Admin opens appointment management
- **WHEN** an admin opens the spa appointment management screen
- **THEN** the system shows compact summary metrics for key appointment states
- **AND** the system provides status, date, and search controls before the appointment list

#### Scenario: Admin reviews an appointment row
- **WHEN** an appointment appears in the filtered list
- **THEN** the system shows package name, customer identity, appointment time, duration, status, consultant assignment, and important customer or internal notes when present
- **AND** available reassignment or cancellation actions are visually distinct from status information

### Requirement: Admin appointment actions remain clear and constrained
The system SHALL present reassignment and cancellation actions with confirmation and copy that reflects their operational impact.

#### Scenario: Admin reassigns an appointment
- **WHEN** an admin starts reassignment for an appointment
- **THEN** the system displays a focused reassignment dialog with consultant id, email, and name inputs
- **AND** confirming reassignment preserves existing appointment lifecycle rules and chat ownership update behavior

#### Scenario: Admin cancels an appointment
- **WHEN** an admin starts cancellation for an active appointment
- **THEN** the system displays a destructive confirmation with the affected appointment context
- **AND** confirming cancellation preserves existing capacity release and appointment cancellation behavior

### Requirement: Admin treatment management supports master-detail review
The system SHALL present admin treatment management so admins can select a treatment plan and review its progress, sessions, photos, and consultant assignment without losing context.

#### Scenario: Admin selects a treatment plan
- **WHEN** an admin taps a treatment plan from the filtered plan list
- **THEN** the system highlights the selected plan and shows its progress summary, status, consultant assignment, and linked chat or appointment context

#### Scenario: Admin reviews treatment sessions
- **WHEN** a treatment plan is selected
- **THEN** the system shows that plan's treatment sessions with session number, schedule state, status label, and date/time information when available

#### Scenario: Admin reviews progress photos
- **WHEN** the selected treatment plan has visible progress photos
- **THEN** the system shows photo thumbnails with photo type, uploader, note preview, and a clear hide action

### Requirement: Admin management screens use consistent visual hierarchy
The system SHALL use consistent spacing, status colors, section hierarchy, and empty states across admin appointment and treatment management screens.

#### Scenario: Admin filters to no results
- **WHEN** admin filters produce no appointments or treatment plans
- **THEN** the system shows a helpful empty state that reflects the current management screen and does not look like a loading failure
