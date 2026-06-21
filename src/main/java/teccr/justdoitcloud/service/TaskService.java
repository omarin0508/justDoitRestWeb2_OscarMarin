package teccr.justdoitcloud.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import teccr.justdoitcloud.data.Task;
import teccr.justdoitcloud.data.User;
import teccr.justdoitcloud.repository.TaskRepository;
import teccr.justdoitcloud.repository.UserRepository;
import teccr.justdoitcloud.service.external.taskgenerator.TaskGenerator;
import teccr.justdoitcloud.service.internal.taskarchiver.TaskArchiver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskGenerator taskGenerator;
    private final UserRepository userRepository;
    private final TaskArchiver taskArchiver;

    public TaskService(TaskRepository taskRepository,
                       TaskGenerator taskGenerator,
                       UserRepository userRepository,
                       TaskArchiver taskArchiver) {
        this.taskRepository = taskRepository;
        this.taskGenerator = taskGenerator;
        this.userRepository = userRepository;
        this.taskArchiver = taskArchiver;
    }

    public List<Task> getTasksForUser(User user) {
        return taskRepository.findByUserId(user.getId());
    }

    public Task addTaskToUser(User user, Task task) {
        task.setUserId(user.getId());
        task.setCreatedAt(LocalDateTime.now());
        Task taskCreated = taskRepository.save(task);

        Optional<User> maybeUser = userRepository.findById(user.getId());
        if (maybeUser.isPresent()) {
            taskArchiver.archiveTask("tasks-new", maybeUser.get(), taskCreated);
        }

        return taskCreated;
    }

    public Task autogenerateTaskForUser(User user) {
        Task task = taskGenerator.generateTask();
        if (task == null) {
            throw new RuntimeException("No se pudo generar la tarea automáticamente");
        }

        task.setUserId(user.getId());
        task.setCreatedAt(LocalDateTime.now());

        return taskRepository.save(task);
    }

    public Optional<Task> getTaskById(Long id) {
        if (id == null || id < 0) {
            return Optional.empty();
        }
        return taskRepository.findById(id);
    }

    public Task updateTaskFields(Long id, Task updatedTask) {
        return taskRepository.findById(id)
                .map(existingTask -> {
                    applyTaskUpdates(existingTask, updatedTask);
                    return taskRepository.save(existingTask);
                })
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
    }

    public void deleteTaskById(Long id) {
        Optional<Task> maybeTask = taskRepository.findById(id);
        if (maybeTask.isEmpty()) {
            throw new RuntimeException("Task not found with id: " + id);
        }

        archiveDeletedTask(maybeTask.get());
        taskRepository.deleteById(id);
    }

    public Optional<Task> getTaskByIdForUser(Long userId, Long id) {
        if (userId == null || id == null || userId < 0 || id < 0) {
            return Optional.empty();
        }

        return taskRepository.findById(id)
                .filter(task -> userId.equals(task.getUserId()));
    }

    public Optional<Task> updateTaskFieldsForUser(Long userId, Long id, Task updatedTask) {
        Optional<Task> maybeTask = getTaskByIdForUser(userId, id);

        if (maybeTask.isEmpty()) {
            return Optional.empty();
        }

        Task existingTask = maybeTask.get();
        applyTaskUpdates(existingTask, updatedTask);

        return Optional.of(taskRepository.save(existingTask));
    }

    public boolean deleteTaskByIdForUser(Long userId, Long id) {
        Optional<Task> maybeTask = getTaskByIdForUser(userId, id);

        if (maybeTask.isEmpty()) {
            return false;
        }

        Task task = maybeTask.get();
        archiveDeletedTask(task);
        taskRepository.deleteById(id);

        return true;
    }

    private void applyTaskUpdates(Task existingTask, Task updatedTask) {
        if (updatedTask == null) {
            return;
        }

        if (updatedTask.getDescription() != null &&
                !updatedTask.getDescription().trim().isEmpty()) {
            existingTask.setDescription(updatedTask.getDescription().trim());
        }

        if (updatedTask.getStatus() != null) {
            existingTask.setStatus(updatedTask.getStatus());
        }
    }

    private void archiveDeletedTask(Task task) {
        Optional<User> maybeUser = userRepository.findById(task.getUserId());

        maybeUser.ifPresent(user -> {
            try {
                taskArchiver.archiveTask("tasks-deleted", user, task);
            } catch (Exception ignored) {
                log.error(
                        "Error archiving task with id {} for user id {}: {}",
                        task.getId(),
                        user.getId(),
                        ignored.getMessage()
                );
            }
        });
    }
}