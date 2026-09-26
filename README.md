# Pet Mounts

A RuneLite plugin that lets you ride your pet like a mount in World of Warcraft.
Your follower grows to a rideable size, you sit on its back, and it walks, runs
and turns with you.

## How to ride

- Right-click your pet and choose **Ride**
- Or press **Alt + M** (change it in the plugin settings)
- Or type `::ride` / `::mount` in chat

To get off: **right-click anywhere and choose "Dismount"** (always there while riding),
press **Alt + M** again, or type `::dismount`.

## Climbing on

When you click **Ride**, your character beckons the pet while the game's own sparkle effect
plays around you. After about a second the mount appears under you in a puff of smoke.
Getting off gives a puff too, and so does hopping off for an action and climbing back on.

All effects are the game's own graphics drawn in the world, recoloured to match the pet you're
riding (pink-red smoke for the dark core, brown for the baby mole, grey for the rock golem).
Under **Effects** you can pick one colour instead, or turn the animation off.

Moving during the climb-on cancels it.

## Which pets can be ridden

Only **your own pets** can be ridden: the pet has to be your follower and be a real pet
you can pick up, so quest companions and other followers don't count.

Every ownable pet in the game (145 pets, 291 in-game versions) was checked by looking at
its model in its idle pose. 91 pets make sense to ride; the rest are refused with a chat
message saying why:

| Refused because | Pets |
|---|---|
| They stand on two legs | Abyssal orphan, Abyssal protector, Aggy, Akkhito, Bran, Butch, the Dagannoth Jr.s, Elidinis' and Tumeken's guardians, the God Wars Jr.s, Greatish guardian, Lil' Bloat, Lil' Creator, Lil' Destructor, Lil' Maiden, Lil' Xarp, Little Nightmare, Midnight, Moxi, Nexling, Noon, Olmlet, Ric, Rift guardian, Rock Golem, Skotos, Smol Heredit, Tektiny, TzRek-Zuk, Vet'ion Jr., Yami, the upright Kalphite Princess |
| Snakes and worms | Huberte, Jal-Nib-Rek, Lil'viathan, Snakeling |
| Water creatures | Kraken, Tiny Tempor |
| Objects | Smolcano, Tangleroot, Vanguard |
| Not shaped for riding | Baron, Kephriti, Muphin, Smoke Devil, Vasa Minirio |
| Too small | Maggot marquess |

**Floating pets can be ridden**, including the Corporeal Beast pet (Dark core and Corporeal
Critter), Chaos Elemental Jr., Phoenix, Wisp, the herons and Quetzin. They're lowered to a
gentle hover so you're never sitting unrealistically high.

Pets released after this version are judged by their model's shape: pets much taller than
they are long (standing upright) are refused.

If you disagree with a call, add pet names to **Always allow** or **Never allow** in the
"Which pets" settings section.

## How each mount is fitted

Every rideable pet has its own measured seat, found on the game's model in its idle pose:

- **Size:** each pet is enlarged until its back is about horse height, so your legs hang
  down naturally. Big pets are never shrunk. The **Mount size** setting scales them all.
- **Seat:** the exact spot on the pet's back where you sit. The plugin follows that spot
  every frame, so you rise, dip and sway with the pet as it walks and idles.
- **Pose:** Wide (legs down both sides, like a horse) for most pets, Extra wide (knees
  spread) for broad pets like spiders, moles and dragons, and Cross-legged on top of
  floating pets.

**Seat height** and **Seat forward/back** are there for fine-tuning if a pet looks off.

## Settings

| Setting | What it does |
|---|---|
| Mount / dismount hotkey | Toggle riding |
| Right-click "Ride" option | Show "Ride" on your pet ("Dismount" is always in the menu while riding) |
| Mount-up animation | The ~1-2 second beckon, sparkles and poof |
| Match pet colours / Effect colour | Effect colours from your pet, or one colour of your choice |
| Hop off for actions | Step off while fighting, skilling or teleporting, then climb back on |
| Stay mounted between sessions | Remount automatically after logging in |
| Mount size | Make every mount bigger or smaller |
| Riding pose | Automatic (the pose fitted to each pet), or choose one yourself |
| Seat height / Seat forward-back | Fine-tune where you sit |

## Good to know

- This is purely visual and client-side. Other players see you walking with your pet as normal.
- The mount uses the pet's own idle, walk and run animations. On heavily enlarged pets,
  animations that move body parts (not just rotate them) can look slightly exaggerated.

## Running it locally

1. Open the folder in IntelliJ IDEA as a Gradle project.
2. Open `build.gradle` and click the green triangle next to the `run` task (or run `./gradlew run`).
3. RuneLite launches with Pet Mounts loaded. With a Jagex account, follow
   https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts to log in to the development client.

## How it works (for developers)

- The real pet and the real player model are hidden with a `RenderCallback`.
- Effects are the game's own graphics (spotanims), read from the client's cache, recoloured
  and drawn as `RuneLiteObject`s.
- The pet is rebuilt as a `RuneLiteObject` from its NPC definition (models, recolours,
  width/height scale), enlarged with `ModelData.scale`, and animated with the live
  follower's idle/walk/run animations.
- The rider is a `RuneLiteObjectController` that draws the player's current animated
  model on the mount's back, following the player's position and facing each client tick.
- Each rideable pet's seat is stored as a triangle on its model plus a position inside it
  (`MountFits.java`, measured offline from the game cache). Each frame the rider is placed on
  that exact spot of the animated mount. Pets without a stored seat get one found at runtime
  by ray-casting down onto the model (`SeatFinder.java`).
- The player's movement animations are overridden with the riding pose while mounted and
  restored on dismount.

## Credits

The seat-following technique and the seated riding poses are adapted from
[Rapid Mounts](https://github.com/RapidUrsa/RapidMounts) by RapidUrsa (BSD 2-Clause).
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
