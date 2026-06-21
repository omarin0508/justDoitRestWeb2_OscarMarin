package teccr.justdoitcloud.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import teccr.justdoitcloud.data.Task;
import teccr.justdoitcloud.data.User;
import teccr.justdoitcloud.service.TaskService;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users/{userId}/tasks")
public class TasksController {

    private final TaskService taskService;

    public TasksController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Iterable<Task> getTasksForUser(@PathVariable Long userId) {
        User user = new User();
        user.setId(userId);
        return taskService.getTasksForUser(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task addTaskToUser(@PathVariable Long userId,
                              @RequestBody(required = false) Task task,
                              @RequestParam(name = "autogenerate", required = false) String autogenerate) {

        User user = new User();
        user.setId(userId);

        boolean auto = autogenerate != null &&
                (autogenerate.isEmpty() || autogenerate.equalsIgnoreCase("true"));

        if (auto) {
            return taskService.autogenerateTaskForUser(user);
        }

        if (task == null) {
            throw new IllegalArgumentException(
                    "Task body is required when autogenerate is not used"
            );
        }

        return taskService.addTaskToUser(user, task);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable Long userId,
                                            @PathVariable Long id) {

        Optional<Task> taskOpt =
                taskService.getTaskByIdForUser(userId, id);

        return taskOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long userId,
                                           @PathVariable Long id,
                                           @RequestBody Task task) {

        Optional<Task> updatedTask =
                taskService.updateTaskFieldsForUser(userId, id, task);

        return updatedTask.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long userId,
                                           @PathVariable Long id) {

        boolean deleted =
                taskService.deleteTaskByIdForUser(userId, id);

        if (!deleted) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.noContent().build();
    }
}