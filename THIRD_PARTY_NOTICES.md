# Third-party notices

Pet Mounts adapts techniques from [Rapid Mounts](https://github.com/RapidUrsa/RapidMounts)
by RapidUrsa (source revision `656a19f`):

- The seat anchor in `SeatAnchor.java`: following the averaged movement of the mount
  vertices nearest the seat so the separately drawn rider moves with the mount's back,
  with the movement clamped to body-sized limits.
- The riding poses in `RiderPose.java`: the stool-sit pose for Saddle, a held frame of
  the Wide pose, the settled loop of the sit emote for Cross-legged, and the hip heights
  estimated from Rapid Mounts' tuned rider heights.
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
