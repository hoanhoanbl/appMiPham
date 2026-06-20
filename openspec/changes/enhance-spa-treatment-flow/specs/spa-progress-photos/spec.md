## ADDED Requirements

### Requirement: Spa packages can require progress photos
The system SHALL let admins configure whether a spa package or treatment template requires progress photos.

#### Scenario: Package does not require photos
- **WHEN** an admin configures a package with `requiresProgressPhotos = false`
- **THEN** treatment sessions created from that package do not require progress photo upload before completion

#### Scenario: Package requires photos
- **WHEN** an admin configures a package with `requiresProgressPhotos = true`
- **THEN** treatment sessions created from that package expose progress photo upload requirements to consultants

### Requirement: Photo policy defines required photo timing
The system SHALL support progress photo policies for no photos, after-session photos, and before-and-after photos.

#### Scenario: After-session policy
- **WHEN** a treatment session uses `photoPolicy = "after_each_session"`
- **THEN** the consultant is prompted to upload after-session photos before completing the session

#### Scenario: Before-after policy
- **WHEN** a treatment session uses `photoPolicy = "before_after_each_session"`
- **THEN** the consultant is prompted to upload both before-session and after-session photos before completing the session

### Requirement: Consultants can upload progress photos per session
The system SHALL let assigned consultants upload progress photos for treatment sessions that belong to their assigned treatment plans.

#### Scenario: Consultant uploads before photo
- **WHEN** the assigned consultant uploads a before-session image for a treatment session
- **THEN** the system stores a progress photo record with type `before`, session context, uploader context, image URL, and timestamp

#### Scenario: Consultant uploads after photo
- **WHEN** the assigned consultant uploads an after-session image for a treatment session
- **THEN** the system stores a progress photo record with type `after`, session context, uploader context, image URL, and timestamp

#### Scenario: Consultant adds photo note
- **WHEN** the consultant adds a note while uploading a progress photo
- **THEN** the system stores the note with the progress photo record

### Requirement: Consultants can skip required photos with a reason
The system SHALL allow required progress photos to be skipped only with a recorded reason.

#### Scenario: Required photo is missing at completion
- **WHEN** a consultant attempts to complete a session that requires progress photos but required photos are missing
- **THEN** the system warns the consultant before completion

#### Scenario: Consultant records skip reason
- **WHEN** the consultant chooses to complete the session without required photos
- **THEN** the system requires a skip reason and stores it with the session completion metadata

### Requirement: No-show and cancelled sessions do not require progress photos
The system SHALL not require progress photo upload for treatment sessions that do not happen.

#### Scenario: Customer no-show
- **WHEN** a consultant marks a treatment session as `no_show`
- **THEN** the system does not require before or after progress photos

#### Scenario: Session cancelled
- **WHEN** a treatment session is cancelled before service delivery
- **THEN** the system does not require progress photos

### Requirement: Customers can view their treatment progress photos
The system SHALL let customers view progress photos that belong to their treatment plans.

#### Scenario: Customer opens session detail
- **WHEN** a signed-in customer opens a treatment session belonging to their treatment plan
- **THEN** the system shows progress photos grouped by session and photo type

#### Scenario: Customer cannot view another customer's photos
- **WHEN** a customer attempts to access progress photos for another customer's treatment plan
- **THEN** the system does not show those photos

### Requirement: Admins can review progress photo records
The system SHALL let admins review progress photo records for treatment oversight and moderation.

#### Scenario: Admin opens treatment plan detail
- **WHEN** an admin opens a treatment plan with progress photos
- **THEN** the system shows photo records with session, customer, consultant, and upload context

#### Scenario: Admin handles inappropriate photo
- **WHEN** an admin identifies an incorrect or sensitive progress photo that should not remain visible
- **THEN** the system provides a way to hide or remove the progress photo record during moderation
