# Understanding JPA and Lombok Annotations

This guide explains `FetchType`, `CascadeType`, `@Builder.Default`, `@ToString.Exclude`,
`@EqualsAndHashCode.Exclude` and `mappedBy`: what each one is, why it matters, and when to use it.
All examples come from this project's `Post` and `UserAccount` entities.

---

## 1. `FetchType`: when related data is loaded

**What it is:** it tells Hibernate whether to load a related entity right away or only when you first use it.

| Value | Behaviour |
|---|---|
| `EAGER` | Loaded at the same time as the owning entity, through a join or an extra query. |
| `LAZY` | Hibernate puts in a placeholder (a *proxy*) and runs the query the first time you call a method on it. |

The defaults differ, which catches many people out:
- `@ManyToOne` and `@OneToOne` default to **EAGER**.
- `@OneToMany` and `@ManyToMany` default to **LAZY**.

**In this project:**
```java
@ManyToOne(fetch = FetchType.LAZY)
private UserAccount userAccount;
```
Loading a post doesn't load its author until you call something like `post.getUserAccount().getUsername()`.

**Why it matters:**
- EAGER loads data you often don't need. With lists it causes the **N+1 problem**: loading 100 posts runs 1 query for the posts plus up to 100 more for the authors. You also can't switch EAGER off for a single query.
- LAZY only works while the database session is still open, usually inside a `@Transactional` method. Touching a lazy field after the session closes throws `LazyInitializationException`. That's why `PostService.getPost()` has `@Transactional(readOnly = true)`: `PostResponse.fromEntity` reads `userAccount` and `likedByUsers` while the session is still open.

**When to use which:** make **everything LAZY**, including `@ManyToOne`. When a query really needs the related data, ask for it in that query with `JOIN FETCH` or `@EntityGraph`:
```java
@Query("select p from Post p join fetch p.userAccount")
List<Post> findAllWithAuthor();
```

---

## 2. `CascadeType`: which operations carry over to related entities

**What it is:** when you run an operation on an entity, the cascade setting decides whether Hibernate repeats it on the related entities.

| Type | When you… | Hibernate also… |
|---|---|---|
| `PERSIST` | save a new entity | saves new related entities |
| `MERGE` | merge (update) an entity | merges related entities |
| `REMOVE` | delete an entity | **deletes related entities** |
| `REFRESH` | reload from the DB | reloads related entities |
| `DETACH` | detach from the session | detaches related entities |
| `ALL` | do any of the above | does the same to related entities |

**Why it mattered in this project:** `Post.userAccount` used to have `cascade = CascadeType.ALL`. That had two effects:
1. Creating a post with `{"userAccount": {...}}` **inserted a new user**, because PERSIST cascaded.
2. Deleting a post would have **deleted its author**, because REMOVE cascaded, along with that user's other data, or it would have failed on foreign keys.

**When to use it:** only from a parent to children whose whole lifecycle belongs to that parent. For example, a `Comment` entity that can't exist without its post:
```java
@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Comment> comments;
```

**When not to use it:**
- **Never use `REMOVE` or `ALL` on `@ManyToOne` or `@ManyToMany`.** The other side is shared: one user writes many posts, and one post is liked by many users.
- Cascade is not needed to save a relationship. `UserAccount.likedPosts` has `PERSIST, MERGE, REFRESH, DETACH`. The `post_likes` row is written because the collection on the owning side changed (see `mappedBy` below), not because of the cascade. The `PERSIST` there means saving a user who holds a *new, unsaved* post would also insert that post, which you probably don't want. The `cascade` attribute could be removed from that field completely.

---

## 3. `@Builder.Default`: keep field initializers when using the builder

**What it is:** a Lombok annotation that makes `@Builder` use the field's initial value when you don't set that field.

**Why it's needed:** `@Builder` **ignores** `= new HashSet<>()`. Without the annotation:
```java
private Set<Post> likedPosts = new HashSet<>();

UserAccount u = UserAccount.builder().username("alice").build();
u.getLikedPosts();          // null, not an empty set
u.getLikedPosts().add(p);   // NullPointerException
```
With `@Builder.Default`, `u.getLikedPosts()` is an empty `HashSet`.

**When to use it:** on any field that has an initial value in a class with `@Builder`: collections, `createdAt = Instant.now()`, `active = true`, and so on. Lombok prints a compile warning when you forget it, so watch for that warning.

---

## 4. `@ToString.Exclude` and `@EqualsAndHashCode.Exclude`: leave fields out of generated methods

**What they are:** `@Data` generates `toString()`, `equals()` and `hashCode()` from **every** field. These two annotations remove a field from the matching method.

**Why the entities need them:** the user–post relationship goes both ways:
```
Post.hashCode()
 → likedByUsers.hashCode()
   → UserAccount.hashCode()
     → likedPosts.hashCode()
       → Post.hashCode()   → … StackOverflowError
```
`toString()` loops the same way, so even logging an entity would crash. The like endpoint ran into this right away: `user.getLikedPosts().add(post)` calls `post.hashCode()`.

They also prevent two quieter problems:
- **Unwanted queries:** calling `hashCode()` or `toString()` on a lazy field loads it from the database, or throws `LazyInitializationException` outside a transaction.
- **Broken sets:** if a hash code depends on a collection, it changes whenever that collection changes. An object stored in a `HashSet` can then no longer be found or removed.

**When to use them:** on an entity, put both on **every relationship field** (`@ManyToOne`, `@OneToMany`, `@ManyToMany`, `@OneToOne`).

In general, avoid `@Data` on JPA entities. Use `@Getter`/`@Setter` and write `equals`/`hashCode` based on the id:
```java
@Override public boolean equals(Object o) {
    return o instanceof Post p && id != null && id.equals(p.getId());
}
@Override public int hashCode() { return getClass().hashCode(); }
```
DTOs like `PostResponse` don't have this problem, so `@Data` is fine there.

---

## 5. `mappedBy`: which side of a two-way relationship owns it

**What it is:** in a two-way relationship, exactly one side owns the database link: the foreign key column, or the join table. `mappedBy` goes on the other side, the *inverse* side, and names the owning field:
```java
// UserAccount: owning side, defines the join table
@ManyToMany
@JoinTable(name = "post_likes", ...)
private Set<Post> likedPosts;

// Post: inverse side, "see UserAccount.likedPosts"
@ManyToMany(mappedBy = "likedPosts")
private Set<UserAccount> likedByUsers;
```

**Why it matters:**
1. **Without it, Hibernate treats the two fields as two separate relationships.** For `@ManyToMany` that means two join tables (`post_likes` and `posts_liked_by_users`) with different data.
2. **Only changes on the owning side are saved:**
   ```java
   user.getLikedPosts().add(post);       // inserts a row into post_likes
   post.getLikedByUsers().add(user);     // writes nothing to the database
   ```
   That's why `UserService.likePost` and `unlikePost` change `user.getLikedPosts()`.

**When to use it:**
- **`@OneToMany` / `@ManyToOne`:** the `@ManyToOne` side always owns the link, because it holds the foreign key column. Put `mappedBy` on the `@OneToMany` side. A `@OneToMany` without it creates an extra join table you don't need.
- **`@ManyToMany`:** choose either side as owner, usually the one your code updates. Put `@JoinTable` on the owner and `mappedBy` on the other side.
- **One-way relationships:** no `mappedBy`. Only one field exists.

Within a single request, the in-memory objects can drift apart, because the inverse side isn't updated automatically. A common fix is a helper method that updates both sides together:
```java
public void like(Post post)   { likedPosts.add(post);    post.getLikedByUsers().add(this); }
public void unlike(Post post) { likedPosts.remove(post); post.getLikedByUsers().remove(this); }
```

---

## Quick reference

| Annotation | Problem it solves | Rule of thumb |
|---|---|---|
| `FetchType.LAZY` | Loading data you don't need, N+1 queries | Make every relationship LAZY and fetch what each query needs |
| `CascadeType` | Saving or deleting child entities with their parent | Only parent → owned children; never REMOVE on shared relationships |
| `@Builder.Default` | Builder leaves initialized fields `null` | Any field with an initial value in a `@Builder` class |
| `@ToString.Exclude` / `@EqualsAndHashCode.Exclude` | Infinite recursion and unwanted lazy loading | Every relationship field on an entity, or avoid `@Data` on entities |
| `mappedBy` | Duplicate join tables, unclear owner | On the non-owning side of every two-way relationship |
