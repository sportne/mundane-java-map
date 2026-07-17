/**
 * Dependency-free read-only support for the strict DTED Level 0, 1, and 2 profile.
 *
 * <p>The package exposes a synchronous file facade, immutable open options, and immutable
 * format-specific resource limits. Successful opens return the format-neutral elevation source
 * contract and retain no file handle. The fixed profile follows MIL-PRF-89020B sections 3.9 through
 * 3.13 and tables I through III.
 */
package io.github.mundanej.map.io.dted;
