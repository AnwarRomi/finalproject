# Smart Learning Management System

Member 3 contribution for BIT1123 Object Oriented Programming  
Antik Anwar Romi · BIT  
SDG 4 Quality Education

This Member 3 package includes the eight-class Java core plus a Swing and
local-file integration demo. Teachers can manage courses and add learning
activities. Students can complete study materials, submit practice quiz scores,
track progress and receive recommended next activities.

## Run on Windows

1. Extract the ZIP and open the folder containing `pom.xml`.
2. In Apache NetBeans, choose **File → Open Project** and select that folder.
   Wait for NetBeans to finish loading the Maven project. Use JDK 17 or newer.
3. Run the project with **F6**. If NetBeans asks for a main class, select
   `my.edu.cityu.lms.ui.SmartLmsFrame`. Leave **Arguments** empty.
4. If you already have an Ant project open, register this archive's `src`
   folder under **Project Properties → Sources**. Set the same main class under
   **Run** and leave arguments empty.

To launch the packaged desktop app directly from PowerShell:

```powershell
java -jar .\SmartLMS.jar
```

The first run opens a seeded classroom. Subsequent runs load the classroom from
`%USERPROFILE%\.smartlms\classroom.dat`. For a fresh practice session, optionally
pass a different file path, for example `java -jar .\SmartLMS.jar demo.dat`.

## Build and verify without an IDE

Use these commands from PowerShell with a JDK on PATH:

```powershell
New-Item -ItemType Directory -Force build/classes, build/test-classes | Out-Null
$sourceFiles = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac --release 17 -encoding UTF-8 -d build/classes $sourceFiles
$testFiles = Get-ChildItem test -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes $testFiles
java -cp 'build/classes;build/test-classes' my.edu.cityu.lms.checks.CoreChecks
java -cp 'build/classes;build/test-classes' my.edu.cityu.lms.checks.StoreChecks
java -cp build/classes my.edu.cityu.lms.demo.SmartLmsCoreDemo
```

## Live demo

1. Select **Aisha Rahman** and **BIT1123 · Java Programming**. One of the three
   activities is complete, giving 33% course progress.
2. Select **OOP Fundamentals Quiz** and submit **60**. Its 70% pass mark leaves
   progress at 33%. The result shows **Retry available**.
3. Select the quiz again and submit **80**. It changes to **Completed**,
   records two attempts, and raises course progress to **67%**.
4. Open **Recommendations** and confirm the completed quiz has disappeared.
5. Switch to **Demo Teacher** to review per-student progress. Create a course,
   add an activity, and enroll a demo student to show the management workflow.
6. Close and relaunch with the same save file. Check that the 80% best score,
   67% course progress and any new course remain.

The demo uses fictional accounts, manually entered quiz scores and sample
resource references. It runs locally as a single-user classroom simulation.
SDG 4 is reflected in the workflow: learners can see unfinished work and choose
a suggested next activity, while teachers can use progress data to identify
learners who may need support.

## Eight core Java classes

| Class | Responsibility |
|---|---|
| `User` | Abstract identity shared by both roles. |
| `Student` | Student role, enrollments, per-activity results, and access times. |
| `Teacher` | Teacher role and identity used to establish course ownership. |
| `Course` | Course identity, owner, editable title and description, and ordered activities. |
| `LearningActivity` | Abstract contract for an activity's type and completion rule. |
| `StudyMaterial` | A PDF or video reference marked complete by the student. |
| `Quiz` | Scored activity completed once the submitted score reaches the pass mark. |
| `LearningService` | Coordinates enrollment, course management, completion, progress, and recommendations. |

The classes are in `src/my/edu/cityu/lms/core`. The demo and verification harness
are outside the eight core classes. The Swing frame and disk adapter are separate integration classes.

## OOP implementation

- **Encapsulation**. Model fields are private, course changes go through
  validated setters, and callers receive read-only copies of collections.
- **Inheritance**. `Student` and `Teacher` extend `User`. `StudyMaterial` and
  `Quiz` extend `LearningActivity`.
- **Abstraction**. `LearningActivity` defines the completion operation and type.
  Each subclass defines what completion means for that activity.
- **Runtime polymorphism**. `LearningService.recordActivity` calls
  `activity.meetsCompletionRule(quizScore)` on a `LearningActivity` reference.
  A PDF or video accepts an unscored completion action. A quiz validates
  the score and compares it with its passing threshold.

## Connect the HTML prototype's screens

Keep one `LearningService` instance for the application's lifetime. To populate
a classroom demo, call `SmartLmsCoreDemo.createDemo()`. For an empty workspace,
construct `new LearningService()` and register teachers and students. Store the
selected user's ID as the current actor and pass IDs from selected table rows
rather than parsing course titles or display names.

| Prototype screen or action | Java call |
|---|---|
| Login screen in demo mode | `findDemoUser(username, role)` returns the matching account or an empty result. Demo student usernames are `aisha`, `daniel`, and `sara`. The demo teacher username is `teacher`. This supports account and role selection for the demo. Credential verification belongs in an authentication layer if the team adds one. |
| Student dashboard and My Progress | `studentProgress(studentId)` returns a row per enrollment. Use `overallStudentProgressPercent(studentId)` for the overall value. |
| Open student course | `openCourse(studentId, courseId)` records the access time and returns the course. Use `course.getActivities()` for the list, `courseProgress(studentId, courseId)` for progress and last access, and `activityResults(studentId, courseId)` for row status. |
| Recommended Materials | `recommendations(studentId, 3)` returns a ranked list with the activity title, type, difficulty and explanation. |
| Mark a PDF or video complete | `markMaterialComplete(studentId, courseId, activityId)`. Refresh progress and recommendations after the action. |
| Submit a scored quiz | `submitQuiz(studentId, courseId, activityId, score)` accepts an integer percentage. Its result exposes completion, attempt count and best score. The GUI can calculate the score or enter a demonstration score. |
| Teacher dashboard and Students & Progress | `teacherDashboard(teacherId)` returns course and student totals, a progress percentage and one row per enrollment in owned courses. |
| Manage Courses | `teacherCourses(teacherId)`, `addCourse(teacherId, courseId, title, description)` and `updateCourse(teacherId, courseId, title, description)`. |
| Manage learning activities | `addActivity(teacherId, courseId, activity)` accepts a `StudyMaterial` or `Quiz`. Use the selected course's activity list to render the screen. |
| Enroll a student | `enrollStudent(teacherId, courseId, studentId)` returns whether it created an enrollment. |

Teacher actions require ownership of the selected course. Student actions require
enrollment. When a form has invalid input, show the relevant
`IllegalArgumentException` message and let the user correct the input. Use
`courseProgress.percent()` to set a progress bar and display a blank or
“Not opened” last-access value when the timestamp is null. A fresh GUI session
can run the in-memory demo with the sample course and resource labels. The demo
labels represent learning resources and can be replaced by real file paths or
links when the team supplies them.

## Progress and recommendation rules

- Progress is the percentage of activities completed, rounded to the nearest
  whole number. A course with no activities displays 0%.
- A PDF or video counts after the student marks it complete. Repeating the
  action leaves its completion count unchanged.
- A quiz counts once it reaches its passing score. Each valid submission is
  recorded as an attempt. A later lower score leaves the best score and
  earned completion intact.
- Overall student progress and the teacher dashboard percentage are calculated
  as total completed activities divided by total assigned activities across the
  relevant enrollments. Each activity carries equal weight. The teacher
  dashboard's enrolled-student count uses distinct student IDs.
- Recommendations exclude completed activities and courses the student has not
  enrolled in. Courses with lower progress come first. Ties favour difficulty
  closest to the learner's current band, then course and activity ID. Below 40%
  the band is Easy, from 40% to 74% it is Medium, and from 75% it is Hard.
  Unfinished quizzes with recorded attempts include a retry reason.

## File handling integration for Member 5

To save the team application's state, read account identities from `getUsers()`,
course values and activity definitions from `getAllCourses()`, and progress from
`enrollmentSnapshots()`. Persist each course owner ID and activity type. For a
PDF or video save its resource reference. For a quiz save its passing score.
The per-enrollment snapshot contains student and course IDs, the last access
time, and results keyed by activity ID. A result has attempt count, completion,
best quiz score if applicable, and last attempt time.

To load, create a new `LearningService`. Register users, add courses and
activities, then call `restoreEnrollment(snapshot)` for each saved enrollment.
It validates the referenced activities, completion and dates. Restore creates the
enrollment, so a separate enrollment call is unnecessary during loading.
`CoreChecks` verifies this handoff using an in-memory round trip. 
`LearningStore` writes a versioned local
file through a temporary file and restores it through the same service API.

## Verification checks

| Check | Verified behaviour |
|---|---|
| T01 | Account lookup, course ownership, valid course edits and duplicates |
| T02 | Material and quiz completion rules, passing score boundary, retake and best score |
| T03 | Recommendation ranking, remaining activities and retry explanations |
| T04 | Student and teacher totals, distinct enrollment counts and access timestamps |
| T05 | Reconstructing the service from exported state and rejecting inconsistent progress |
| T06 | Saving, reloading and replacing a classroom file while preserving results |
| T07 | Rejecting an incomplete classroom file |

The assignment brief uses Level 2 collaborative AI. Record material AI assistance
in the supplied declaration and sign it after reviewing and verifying your work.
