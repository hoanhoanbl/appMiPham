## ADDED Requirements

### Requirement: Consultant chat inbox opens a pure chat screen
The system SHALL route consultant chat conversations to a dedicated message-only chat screen.

#### Scenario: Consultant opens conversation from chat tab
- **WHEN** a consultant taps a customer conversation in the chat tab
- **THEN** the system opens a chat screen that shows conversation header, messages, and message composer
- **AND** the screen does not show appointment action buttons, treatment plan creation controls, session cards, or progress photo upload controls

#### Scenario: Chat screen sends consultant message
- **WHEN** the assigned consultant sends a non-empty message from the dedicated chat screen
- **THEN** the system writes the message to the conversation thread and updates the thread last-message metadata

### Requirement: Consultant chat header links to customer profile
The system SHALL let consultants open customer context intentionally from the chat header or customer name.

#### Scenario: Consultant opens customer profile from chat
- **WHEN** a consultant taps the customer name or avatar in the chat screen header
- **THEN** the system opens a customer profile/history screen for that conversation's customer

#### Scenario: Customer profile shows customer context
- **WHEN** the customer profile/history screen opens
- **THEN** the system shows customer identity information, active treatment plans, appointment history, no-show history, and progress-photo context where available

### Requirement: Appointment operations stay outside chat
The system SHALL keep operational appointment and treatment session actions in appointment or treatment detail screens rather than in the chat screen.

#### Scenario: Consultant needs to complete a session
- **WHEN** a consultant needs to complete, cancel, reschedule, mark no-show, or upload progress photos for a treatment session
- **THEN** the system provides those actions from the schedule or appointment/treatment detail area, not from the message-only chat screen

#### Scenario: Consultant opens appointment detail from schedule
- **WHEN** a consultant taps an appointment from the schedule tab
- **THEN** the system opens an operational detail screen for that appointment and its linked treatment session

### Requirement: Chat respects consultant assignment
The system SHALL allow consultants to send and read chat messages only for threads assigned to them.

#### Scenario: Assigned consultant accesses chat
- **WHEN** the signed-in consultant is the consultant stored on a chat thread
- **THEN** the consultant can read and send messages in that thread

#### Scenario: Unassigned consultant is blocked
- **WHEN** the signed-in consultant is not assigned to the chat thread
- **THEN** the system does not allow that consultant to send messages in the thread

### Requirement: Chat remains available across treatment sessions
The system SHALL maintain a single customer-consultant conversation for a treatment package journey.

#### Scenario: Customer schedules later session
- **WHEN** a customer schedules a later session in the same treatment plan
- **THEN** the existing consultation chat remains linked to the treatment plan and does not create a separate conversation for each session by default

#### Scenario: Appointment-only chat remains compatible
- **WHEN** an existing chat thread has appointment context but no treatment plan id
- **THEN** the chat inbox and chat screen can still open the thread for the assigned consultant
