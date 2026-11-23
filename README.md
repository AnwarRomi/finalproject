# Campus Food Rescue

Campus Food Rescue is a Java Swing simulator for SDG 12, Target 12.3. A campus
coordinator registers surplus food and available recipient capacity, previews
a redistribution plan, and confirms the selected plan. The app tracks portions
and saves each confirmed pickup locally.

## Run the demo

Install Java 17 or later. In the folder containing the packaged JAR, run:

```text
java -jar CampusFoodRescue.jar --demo
```

Select **Allocation → Preview allocation → Confirm allocation**, then open
**History**. The initial demo time is **2026-11-24 10:00**. A fresh demo starts
with 60 registered portions and proposes 33 portions across four pickups. After
confirmation, 19 eligible portions and 8 expired portions remain. Close and
reopen the demo to check that the confirmed pickups persist. To repeat the
initial example, run the JAR from a new working folder.

Run without `--demo` to start a separate workspace for your own records. The
application creates the `data` folder in its current working directory. Demo
records are saved to `data/demo.tsv` and regular records to
`data/campus-rescue.tsv`. One application window can write to each file at a
time. Use a writable local folder that supports atomic file replacement.

## Build and test from source

Use a JDK, which provides both `javac` and `jar`. No external libraries or build
tools are required. Run these commands from the project root.

**Windows PowerShell**

```powershell
New-Item -ItemType Directory -Force build/classes, build/test-classes | Out-Null
$sourceFiles = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$testFiles = Get-ChildItem test -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac --release 17 -encoding UTF-8 -d build/classes $sourceFiles
javac --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes $testFiles
java '-Djava.awt.headless=true' -cp 'build/classes;build/test-classes' my.edu.cityu.rescue.RescueChecks
jar --create --file CampusFoodRescue.jar --main-class my.edu.cityu.rescue.ui.RescueFrame -C build/classes .
java -jar CampusFoodRescue.jar --demo
```

**macOS or Linux**

```bash
mkdir -p build/classes build/test-classes
find src -name '*.java' -print > build/sources.txt
find test -name '*.java' -print > build/tests.txt
javac --release 17 -encoding UTF-8 -d build/classes @build/sources.txt
javac --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes @build/tests.txt
java -Djava.awt.headless=true -cp build/classes:build/test-classes my.edu.cityu.rescue.RescueChecks
jar --create --file CampusFoodRescue.jar --main-class my.edu.cityu.rescue.ui.RescueFrame -C build/classes .
java -jar CampusFoodRescue.jar --demo
```

## Project structure

| Package | Classes and responsibilities |
| --- | --- |
| `model` | `FoodBatch`, `ShelfStableBatch` and `ChilledBatch` model food and storage rules. `Recipient` manages session capacity. `Allocation` records a transfer. |
| `service` | `RescueService` registers records, calculates and confirms plans, and reconciles portion counts. |
| `storage` | `RescueStore` reads and writes the versioned UTF-8 data file. |
| `ui` | `RescueFrame` launches the Swing window and collects operator input. |

## Planning rules

1. Sort food by the earliest use-by time, using batch ID to break ties.
2. Skip exhausted batches and batches at or past their use-by time.
3. Try ambient recipients first for shelf-stable food to preserve refrigerated
   capacity. Chilled food can only be allocated to refrigerated recipients.
4. Each pickup uses the smaller of the remaining food and recipient capacity.
   Continue splitting the batch while eligible capacity remains.
5. Confirmation saves the plan and updates stock and capacity. A data or time
   change clears the preview in the interface.

Recipient capacity is defined for the whole session. The demo uses fictional
data. Allocated portions represent simulated pickups and do not measure food
waste avoided or verify food safety.

For the presentation, see [PRESENTATION_GUIDE.md](PRESENTATION_GUIDE.md).
For SDG 12 and Target 12.3, see the [United Nations goal page](https://sdgs.un.org/goals/goal12).
