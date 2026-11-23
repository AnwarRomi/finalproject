# Campus Food Rescue presentation guide

## Timing for a 15-minute presentation

| Time | Slides and demo | Speaker |
| --- | --- | --- |
| 0:00–2:45 | Introduce the campus coordination problem, SDG 12.3 and project objectives. | Member 1 |
| 2:45–6:00 | Explain the UML, the two food subclasses and the four OOP principles. | Member 2 |
| 6:00–9:00 | Walk through allocation rules, collection use, the snapshot file and testing. | Member 3 |
| 9:00–12:45 | Operate the Swing demo. Preview, confirm and reopen the saved history. | Member 4 |
| 12:45–15:00 | Interpret the results, describe the limitations and conclude. | Member 5, or Member 4 in a group of four |

Use your own words. In a video, the brief asks all group members to present
with their faces clearly visible. For a physical presentation, give each
member a speaking role and demonstrate the application live.

## Demonstrate the application

1. Launch the JAR with `--demo` from a fresh working folder. Show the four
   batches and three recipients. The time is 24 November 2026 at 10:00.
2. Show that the 8 Rice box portions expired at 09:00. The 24 Yoghurt cup
   portions have the earliest valid deadline, 11:00.
3. Open **Allocation** and choose **Preview allocation**. The first pickup gives
   12 yoghurt portions to Community Fridge. The next two split 18 Bread rolls
   across Student Pantry and Residence Hall. Residence Hall takes 3 Cracker packs.
4. Point out that **Allocated portions** stays at zero during preview. Choose
   **Confirm allocation** and show the new totals: 33 allocated, 19 eligible
   remaining and 8 expired remaining. The 33 portions use all available capacity.
5. Open **History**, close the app and reopen it from the same working folder.
   Show the same four saved pickups. A second preview produces no allocations
   until capacity or inventory conditions change.

## Suggested discussion prompts

- Why use `FoodBatch` references? The service calls one method and Java chooses
  the storage rule implemented by the actual subclass.
- Why prefer ambient recipients for shelf-stable food? It preserves access to
  refrigeration for chilled food later in the queue.
- What could the prototype do better? Add transport constraints, campus-approved
  food-handling procedures and multi-user coordination before a real pilot.
- What can the 33 portions show? They demonstrate the planning logic and the
  capacity constraint on simulated data. A real pilot would need pickup records
  and a baseline to assess waste reduction.
