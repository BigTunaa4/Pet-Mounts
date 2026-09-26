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

When you click **Ride**, your character beckons the pet for about a second while a glowing
circle and sparkles swirl around you. Then the mount appears under you in a poof of smoke and
sparkles. Getting off gives a poof too.

The circle, sparkles and poof are coloured to match the pet you're riding (a pink-red poof
for the dark core, brown for the baby mole, silver and gold for the rock golem). You can turn
that off and pick one colour instead, or turn the animation off entirely, under **Effects**.

Moving during the climb-on cancels it, just like a mount cast in WoW.

## Which pets can be ridden

Only **your own pets** can be ridden: the pet has to be your follower and be a real pet
you can pick up, so quest companions and other followers don't count.

Pets that would be awkward or unrealistic are refused, with a chat message saying why:

| Refused because | Examples |
|---|---|
| Objects | Pet rock, Toy cat, Spooky chair, Humphrey Dumphrey, Smolcano, Rift guardian, Tangleroot |
| Humanoid | Pet General Graardor, Pet K'ril Tsutsaroth, Pet Kree'arra, Pet Zilyana, Vet'ion Jr., Nexling, Noon, Butch, Smol Heredit, Yami, Bran |
| Slithers | Pet snakeling, Jal-Nib-Rek |
| Water creatures | Pet kraken, Tiny tempor |
| Too small | Maggot marquess |

Good mounts include Baby mole, Callisto cub, Hellpuppy, Ikkle Hydra, Kalphite Princess,
Olmlet, the Dagannoth pets, Phoenix, Prince Black Dragon, Vorki, Youngllef, Beef, Gull,
Beaver, Giant squirrel, Rock golem, Rocky, Herbi, Bloodhound, Mr McGroot, and all cats and dogs.

**Floating pets are rideable too**: Pet dark core (Corporeal Beast), Corporeal critter,
Rift guardian, Pet chaos elemental, Pet smoke devil, Abyssal protector, Wisp, Skotos,
Little Nightmare, Tumeken's guardian and others. While ridden they're lowered to a gentle
hover just off the ground, so you're never sitting unrealistically high in the air.
No rider ever sits higher than about three-quarters of a player's height.

Pets not on either list (including new ones) are judged by their model's shape: pets that
are much taller than they are long (like a person standing up) are refused.

If you disagree with a call, add pet names to **Always allow** or **Never allow** in the
"Which pets" settings section.

## How sizing works

Each pet is measured when you mount up, then enlarged toward the **Mount height**
setting (default 140 units; a player is about 200 units tall, a tile is 128).

| Pet height | Growth | Result |
|---|---|---|
| Tiny (pet rock, ~20) | capped at 4.5x | ~90 |
| Small (~40) | 3.5x | 140 |
| Medium (~70) | 2x | 140 |
| Already big (140+) | none | unchanged |

Big pets are never shrunk. **Max growth** keeps tiny pets from turning into
giants, and **Size tweak** scales everything up or down on top.

## Settings

| Setting | What it does |
|---|---|
| Mount / dismount hotkey | Toggle riding |
| Right-click "Ride" option | Show "Ride" on your pet ("Dismount" is always in the menu while riding) |
| Mount-up animation | The ~1-2 second beckon, sparkles and poof |
| Match pet colours / Effect colour | Effect colours from your pet, or one colour of your choice |
| Hop off for actions | Step off while fighting, skilling or teleporting, then climb back on |
| Stay mounted between sessions | Remount automatically after logging in |
| Mount height / Max growth / Size tweak | Control how big pets get |
| Riding pose | Seated (magic carpet pose) or Standing |
| Seat height / Seat forward-back | Line the rider up with each mount's back |

## Good to know

- This is purely visual and client-side. Other players see you walking with your pet as normal.
- Tall, thin or unusually shaped pets may need the seat sliders adjusted.
- The mount uses the pet's own idle, walk and run animations. On heavily enlarged pets,
  animations that move body parts (not just rotate them) can look slightly exaggerated.

## Running it locally

1. Open the folder in IntelliJ IDEA as a Gradle project.
2. Open `build.gradle` and click the green triangle next to the `run` task (or run `./gradlew run`).
3. RuneLite launches with Pet Mounts loaded. With a Jagex account, follow
   https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts to log in to the development client.

## How it works (for developers)

- The real pet and the real player model are hidden with a `RenderCallback`.
- The pet is rebuilt as a `RuneLiteObject` from its NPC definition (models, recolours,
  width/height scale), enlarged with `ModelData.scale`, and animated with the live
  follower's idle/walk/run animations.
- The rider is a `RuneLiteObjectController` that draws the player's current animated
  model at `ground - seatHeight`, following the player's position and facing each client tick.
- The player's movement animations are overridden with the riding pose while mounted and
  restored on dismount.
