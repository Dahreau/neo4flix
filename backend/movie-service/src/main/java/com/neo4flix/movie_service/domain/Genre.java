package com.neo4flix.movie_service.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

// name is the business key/id - no surrogate UUID needed for a small closed taxonomy,
// and it dedupes genres for free via the genre_name_unique constraint.
@Node("Genre")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "name")
public class Genre {

    @Id
    private String name;

    public static Genre of(String name) {
        return new Genre(name);
    }
}
