# Third-party notices

Pet Mounts adapts techniques from [Rapid Mounts](https://github.com/RapidUrsa/RapidMounts)
by RapidUrsa (source revision `656a19f`):

- In `SeatTracker.java`: moving the separately drawn rider by tracking vertices of the
  animated mount model.
- The riding poses in `RiderPose.java`: the stool-sit pose, a held frame of the Wide pose,
  the toboggan crouch for Extra wide, and the settled loop of the sit emote for Cross-legged.
- In `PetMountsPlugin.java` / `SaddleMesh.java`: building a custom saddle by reshaping a spare
  model loaded from the game (merged so its arrays are fresh, with unused faces left empty). The
  saddle's shape, fitting and colours are Pet Mounts' own.
- In `MountEffects.java`: building effects from the game's own graphics as
  `RuneLiteObject`s, and handling graphic definition opcode 10 as a flag with no data.

Rapid Mounts is distributed under the following licence:

BSD 2-Clause License

Copyright (c) 2026, RapidUrsa
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
