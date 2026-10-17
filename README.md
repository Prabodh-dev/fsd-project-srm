# ExamEdge: Secure Online Examination and Result Analytics Platform

ExamEdge is a full stack web application that I built for my full stack development project. It lets teachers create timed multiple choice exams, lets students take them in a monitored browser session, and gives everyone clear results and analytics afterwards. It is written in Java 17 with Spring Boot and server rendered Thymeleaf pages, and it runs with zero setup because the default database is a local H2 file.

## Features

### Accounts and security
- Three roles: ADMIN, TEACHER and STUDENT.
- Public registration for teachers and students only. Admin accounts cannot be self registered.
- Session based form login with BCrypt password hashing, custom login page, logout, and custom 403 and 404 pages.
- Route level and method level authorization, CSRF protection on every form and on every fetch call.
- Account lockout for 10 minutes after 5 failed sign in attempts.
- Disabled or re-roled accounts are signed out on their next request.
- Profile page to change the display name and the password.

### Admin
- Dashboard with totals for users, exams and attempts, plus a Chart.js chart of registrations and attempts over the last 14 days.
- User management with search, role filter, enable or disable, role change and delete.
- List of all exams with the ability to remove inappropriate ones.

### Teacher
- Dashboard of my own exams with Draft, Scheduled, Live and Ended badges, search and pagination.
- Create, edit, publish, unpublish and delete exams.
- Question builder: add, edit, delete and reorder questions, each with 2 to 6 options and exactly one correct option.
- Bulk import of questions from a CSV file with per row error reporting, and a downloadable sample CSV.
- Results page per exam with score, percentage, pass or fail and time taken, plus CSV export.
- Analytics per exam: average, highest and lowest score, pass rate, average completion time, score distribution histogram and per question accuracy chart with the hardest and easiest questions.
- Individual attempt view that compares the student's answers with the answer key, and the proctoring events of that attempt.

### Student
- Dashboard with live and upcoming exams, personal average and best score, and a score trend line chart.
- Instructions page before an attempt starts.
- Exam engine:
  - The timer is kept on the server. The start time is stored and the remaining seconds are computed on the server. A submission later than start time plus duration plus a 5 second grace is rejected.
  - Questions and options are shuffled per student when the exam has shuffle enabled. The order is stored, so a reload shows the same order.
  - Every chosen option is saved with a fetch call. Reloading or reconnecting restores the answers and the remaining time from the server.
  - Question navigator showing answered, unanswered and flagged questions.
  - Countdown that turns red in the last minute and auto submits at zero.
  - Expired attempts are also closed by the server when they are next accessed and by a scheduled job that runs every minute.
  - Tab switches and window blurs are logged as proctoring events with a visible warning counter. The attempt is auto submitted when the exam's maximum number of violations is reached.
  - Copy, paste and the context menu are blocked on the exam page.
  - A confirmation dialog shows the number of unanswered questions before a manual submit.
- Automatic grading on submit and a result page with a full per question review.
- Leaderboard per exam (top 10) and a personal history page.
- Print friendly result page and a server generated PDF result (OpenPDF).

### Cross cutting
- Global exception handling with friendly error pages, validation messages on every form, and flash messages.
- Pagination and search on list pages.
- Light and dark theme toggle that is remembered in the browser.
- Responsive layout that works at phone width and keyboard friendly exam page.

## Tech stack
- Java 17, Spring Boot 3.3, Maven with the Maven wrapper
- Spring Web, Spring Security, Spring Data JPA, Bean Validation
- Thymeleaf with thymeleaf-extras-springsecurity
- H2 file database by default, MySQL through the `mysql` profile
- OpenPDF for PDF generation
- Hand written CSS and vanilla JavaScript, with Chart.js 4 served locally from `/static/js`
- JUnit 5, Spring Boot Test, MockMvc and Spring Security Test

## Data model
- **users**: id, full_name, email (unique), password_hash, role, enabled, failed_login_attempts, locked_until, created_at.
- **exams**: id, title, description, teacher_id (user), duration_minutes, start_time, end_time, total_marks, passing_percentage, published, shuffle_questions, allow_retake, max_violations (default 3), created_at. An exam has many questions.
- **questions**: id, exam_id, question_text, marks, display_order. A question has many options.
- **options**: id, question_id, option_text, is_correct. Each question has 2 to 6 options and exactly one correct option, which is validated by the application before saving or publishing.
- **attempts**: id, exam_id, student_id, started_at, submitted_at, score, status (IN_PROGRESS, SUBMITTED, AUTO_SUBMITTED), question_order (the stored per student order of question ids) and active_marker. The unique constraint on (exam_id, student_id, active_marker) lets a student have only one active attempt per exam, because the marker is cleared when the attempt finishes. A second completed attempt is only allowed when the exam has allow_retake set.
- **attempt_answers**: id, attempt_id, question_id, selected_option_id (nullable), flagged_for_review. One row per question per attempt, unique on (attempt_id, question_id).
- **proctoring_events**: id, attempt_id, event_type, occurred_at.

Relationships: a teacher (user) owns many exams; a student (user) has many attempts; an exam has many questions and many attempts; an attempt has many attempt_answers and many proctoring_events; each attempt_answer points to one question and optionally to the selected option.

## Project structure
```
src/main/java/dev/prabodh/examedge
  config       security, login handlers, account state filter, clock, scheduled job, data seeder
  controller   MVC controllers and the exam JSON API
  dto          form objects and read only view records
  entity       JPA entities and enums
  exception    custom exceptions and global handlers
  repository   Spring Data repositories
  service      business logic: exams, questions, attempts, grading, results, analytics, PDF
  util         timer, CSV helpers, question draft validation, paging helpers
src/main/resources
  templates    Thymeleaf pages and layout fragments
  static       css, js (including Chart.js) and the sample questions CSV
  application.properties, application-mysql.properties
src/test/java  unit tests and MockMvc tests
```

## Running the application

Requirements: JDK 17 or newer.

### Default (H2 file database)
```
./mvnw spring-boot:run
```
Open http://localhost:8080. The database file is created under `./data` on first start and the seed data is added automatically.

### MySQL profile
Create nothing by hand; the database is created if the user is allowed to. Set the connection details with environment variables and activate the profile:
```
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=examedge
export DB_USERNAME=root
export DB_PASSWORD=secret
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

### Build a jar
```
./mvnw clean package
java -jar target/examedge-1.0.0.jar
```

## Seeded accounts

| Role    | Email                  | Password    |
|---------|------------------------|-------------|
| Admin   | admin@examedge.com     | Admin@123   |
| Teacher | teacher@examedge.com   | Teacher@123 |
| Student | student@examedge.com   | Student@123 |

The seeder also creates a published sample exam called "Computer Science Fundamentals" with 10 questions, so the student flow can be tried straight away. Change these passwords before using the application anywhere other than a local machine.

## CSV question import
The import expects this header and one question per row:
```
question,marks,option1,option2,option3,option4,option5,option6,correct
```
`correct` is the number (1 to 6) of the correct option column. Empty option columns are ignored. A sample file is available at `src/main/resources/static/samples/questions-sample.csv` and from the questions page of every exam.

## Running the tests
```
./mvnw clean verify
```
The suite contains unit tests for the grading service, the timer rules and the CSV importer, service level tests for the attempt engine (expiry, grace window, violation limit, retakes), and MockMvc tests for the security rules, registration, account lockout and the main student flow from starting an attempt to downloading the PDF.
