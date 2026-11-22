package com.penvire.lab.persistence;

import java.util.UUID;

/** Test domain object. The domain assigns the ID, as Identity will with a key's {@code kid}. */
record Widget(UUID id, String name) {}
