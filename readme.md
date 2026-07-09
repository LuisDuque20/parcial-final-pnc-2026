# Luis Fernando Duque Pacheco 00013423

## Indicaciones

Recientemente, se utilizó AI para crear un sistema de gestion de una biblioteca, el cual ha generado varios errores, su trabajo es arreglarlo. Dado el siguiente caso de uso, explique y/o resuelva cada problema según se le pida.

---

## Consideraciones

La libreria crea automaticamente un correo con los nombres de la persona

---

## Problemas

### 1. Filtro por autor y género (10%)

QA ha reportado que el endpoint para obtener los libros puede filtrar por **autor** y por **género**, o por cualquiera de los dos de manera individual.

Actualmente:

- Filtrar únicamente por autor funciona correctamente.
- Filtrar únicamente por género funciona correctamente.
- Filtrar por **autor y género al mismo tiempo** provoca que el servidor falle.

**Instrucción:** Explique la causa del problema y resuélvalo.


**Archivo:** `BookService.java` / `BookRepository.java`

**Causa:**
Había dos errores combinados:

1. `BookRepository` declaraba el método derivado como:
   ```java
   List<Book> findByAuthorAndGenre(String author, String genre);
   ```
   pero el campo `genre` en la entidad `Book` es de tipo `Genre` (un enum), no `String`. Spring Data JPA genera la consulta JPQL comparando el atributo `genre` (enum) contra un parámetro de tipo `String`, lo cual provoca un error de tipos en tiempo de ejecución al ejecutar la query.

2. Además, en `BookService.getAllBooks()` el método se invocaba con los argumentos invertidos:
   ```java
   bookRepository.findByAuthorAndGenre(genre, author);
   ```
   es decir, se pasaba el género en la posición del autor y viceversa, lo cual —incluso si los tipos fueran correctos— devolvería resultados equivocados.

Por eso, filtrar solo por autor o solo por género funcionaba (usan `findByAuthor` y `findByGenre`, que están bien definidos), pero combinar ambos filtros fallaba.

**Solución:**
- Se cambió la firma del repositorio para que el segundo parámetro sea del tipo correcto (`Genre`):
  ```java
  List<Book> findByAuthorAndGenre(String author, Genre genre);
  ```
- Se corrigió el orden de los argumentos y se convirtió el `String` recibido a `Genre` en el servicio:
  ```java
  bookRepository.findByAuthorAndGenre(author, Genre.valueOf(genre.toUpperCase()));
  ```

---

### 2. Error al volver a prestar un libro (10%)

Un usuario reportó que al pedir prestado el libro **The Selfish Gene**, devolverlo e intentar pedirlo prestado nuevamente, el servidor falla.

**Instrucción:** Explique la causa del problema y resuélvalo.


**Archivo:** `MovementService.java`

**Causa:**
En `createMovement()`, cuando se presta un libro y su `availableCount` llega a 0, se marca `available = false`. Sin embargo, en la rama de **devolución** (`else`) solo se incrementaba `availableCount`, pero **nunca se volvía a poner `available = true`**:

```java
} else {
    book.setAvailableCount(book.getAvailableCount() + 1);
}
```

"The Selfish Gene" tiene `availableCount = 1` en los datos iniciales. Al prestarlo, el contador baja a 0 y `available` pasa a `false`. Al devolverlo, el contador vuelve a 1, pero `available` se queda en `false` porque nunca se restaura. Al intentar pedirlo prestado de nuevo, la validación `if (!book.isAvailable())` lanza una excepción (`Book is not available`), que el `GlobalExceptionHandler` convierte en un error 500.

**Solución:**
Se agregó la restauración del flag `available` cuando el contador vuelve a ser mayor que 0:
```java
} else {
    ...
    book.setAvailableCount(book.getAvailableCount() + 1);
    if (book.getAvailableCount() > 0) {
        book.setAvailable(true);
    }
}
```

---
---

### 3. Cantidad de libros por género (10%)

Existe un endpoint que devuelve la cantidad de libros disponibles por género. Sin embargo, actualmente dicho endpoint falla.

**Instrucción:** Explique la causa del problema y resuélvalo.

**Archivo:** `BookService.java` (`getGenresAvailable`)

**Causa:**
El método recorre todos los libros y llama a `book.getGenre().name()` sin validar que `genre` no sea `null`. En `data.sql`, el libro **"The Art of War"** se inserta con `genre = NULL`. Al llegar a ese registro, `book.getGenre()` devuelve `null` y `.name()` lanza un `NullPointerException`, lo cual tumba el endpoint con un error 500.

**Solución:**
Se agregó una validación para omitir libros sin género asignado (evitando el `NullPointerException`) y así el conteo se calcula correctamente para el resto:
```java
for (Book book : books) {
    if (book.getGenre() == null) {
        continue;
    }
    String genreName = book.getGenre().name();
    countByGenre.put(genreName, countByGenre.getOrDefault(genreName, 0L) + 1);
}
```
---

### 4. Error al consultar un libro por ID (10%)

Un miembro del equipo de frontend reporta que la siguiente llamada falla:

```http
GET /books?id=ed16ed1e-7017-4697-a08a-d28c09a74acf
```

**Instrucción:** Explique la causa del problema.


**Causa:**
El controlador expone dos endpoints distintos:
```java
@GetMapping("/{id}")             // GET /books/{id}  -> devuelve UN libro
public ResponseEntity<Book> getBookById(@PathVariable UUID id) { ... }

@GetMapping
public ResponseEntity<List<Book>> getAllBooks(
        @RequestParam(required = false) String author,
        @RequestParam(required = false) String genre) { ... }  // GET /books -> devuelve una LISTA
```

La llamada del frontend:
```
GET /books?id=ed16ed1e-7017-4697-a08a-d28c09a74acf
```
no coincide con la ruta `/books/{id}` (esperaría el ID como parte de la URL, no como *query param*). En cambio, coincide con `GET /books`, que **no tiene ningún parámetro `id`** definido — Spring simplemente ignora ese *query param* desconocido y ejecuta `getAllBooks(null, null)`, devolviendo la lista completa de libros en lugar de un único objeto `Book`.

Esto no genera un error 500 en el backend, pero sí "falla" desde la perspectiva del frontend: este espera un objeto `Book` y recibe un arreglo (`List<Book>`), lo que provoca un error al deserializar/consumir la respuesta en el cliente.

**La forma correcta de llamar al endpoint es:**
```
GET /books/ed16ed1e-7017-4697-a08a-d28c09a74acf
```

---
### 5. Error al crear un libro (10%)

QA ha reportado que el siguiente payload enviado al endpoint `POST /books` provoca un error:

```json
{
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "genre": "classic",
  "isbn": "978-0132350884",
  "available": true,
  "availableCount": 5
}
```

**Instrucción:** Explique la causa del problema.


**Causa:**
El enum `Genre` define sus constantes en mayúsculas:
```java
public enum Genre {
    CLASSIC, CRIME, SELF_HELP, NOVEL, SCIENCE, ECONOMICS, POLITICS
}
```

El payload enviado por QA usa `"genre": "classic"` en minúsculas. En `BookService.createBook()`, el género se convertía así:
```java
book.setGenre(Genre.valueOf(dto.getGenre()));
```

`Genre.valueOf(String)` es **sensible a mayúsculas/minúsculas**: solo acepta exactamente el nombre de la constante (`"CLASSIC"`). Como recibe `"classic"`, lanza `IllegalArgumentException: No enum constant ... Genre.classic`, que el `GlobalExceptionHandler` traduce en un error 500.

---

### 6. Devolución de libros no prestados (20%)

QA ha reportado que un usuario es capaz de devolver libros que nunca ha solicitado en préstamo.

**Instrucción:**

- Confirme si este comportamiento es realmente posible.
- Si es posible, explique la causa y resuelva el problema.
- Si no es posible, explique por qué, haciendo referencia al código correspondiente.


**Causa:**
El método `MovementController.returnBook()` invoca `MovementService.returnBook()` → `createMovement(dto, MovementType.RETURN)`. Este método:

```java
private Movement createMovement(MovementRequestDto dto, MovementType type) {
    Lector lector = lectorRepository.findByEmail(dto.getEmail())
            .orElseThrow(...);
    Book book = bookRepository.findByIsbn(dto.getIsbn())
            .orElseThrow(...);

    if (type == MovementType.BORROWING) {
        // valida disponibilidad
    } else {
        book.setAvailableCount(book.getAvailableCount() + 1);   // <-- sin ninguna validación
    }

    bookRepository.save(book);

    Movement movement = new Movement();
    movement.setLector(lector);
    movement.setBook(book);
    movement.setTimestamp(Instant.now());
    movement.setType(type);

    return movementRepository.save(movement);
}
```

Solo valida que el `lector` y el `book` existan (por email e ISBN respectivamente). **No verifica en ningún momento que ese lector tenga realmente ese libro prestado** antes de aceptar la devolución. Por lo tanto, cualquier lector registrado puede "devolver" cualquier libro existente, incrementando artificialmente su `availableCount` sin que exista un préstamo previo asociado — esto corrompe el inventario (puede hacer que `availableCount` supere la cantidad real de ejemplares).

**Solución:**
Se agregó una consulta al repositorio de movimientos para obtener el último movimiento (`BORROWING` o `RETURN`) registrado entre ese lector y ese libro:

```java
// MovementRepository.java
Optional<Movement> findTopByLectorAndBookOrderByTimestampDesc(Lector lector, Book book);
```

Y en `MovementService.createMovement()`, antes de procesar una devolución, se valida que el último movimiento de ese par lector-libro haya sido un `BORROWING` (es decir, que el libro esté efectivamente prestado a ese lector y no devuelto ya):

```java
} else {
    Movement lastMovement = movementRepository
            .findTopByLectorAndBookOrderByTimestampDesc(lector, book)
            .orElse(null);
    if (lastMovement == null || lastMovement.getType() != MovementType.BORROWING) {
        throw new RuntimeException("This lector has not borrowed this book");
    }
    book.setAvailableCount(book.getAvailableCount() + 1);
    if (book.getAvailableCount() > 0) {
        book.setAvailable(true);
    }
}
```

Con esto, si un lector intenta devolver un libro que nunca pidió prestado (o que ya devolvió), el sistema rechaza la operación con un error controlado en lugar de aceptar la devolución fantasma.
---
