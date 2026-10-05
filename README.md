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

## Ride any pet

Don't have the pet you want? Pick any rideable pet from the **Mount** list at the top of the Mount Stable and
press **Ride**. You ride it just like your own: same saddle, reins, animations and riding motion. Choose
**The pet following you** to go back to riding your own pet. Right-clicking your pet and choosing **Ride** also
switches back to it.

The list also has creatures from all over Gielinor: every kind of dragon (green to rune, lava, brutal, baby,
frost, crystalline, corrupted and revenant dragons, plus the King Black Dragon, Elvarg and Vorkath), drakes,
wyrms and wyverns, the gnomes' battle tortoise, unicorns, camels, cows, bulls, rams, bears, wolves, hellhounds,
Cerberus, Callisto, crocodiles, kalphites, Venenatis, Sarachnis, a mammoth, a rhino, a giant frog, a
penguin, a chicken and, of course, a pet rock. Each one is sized and fitted with a saddle so you sit on its back.

Two-legged pets carry you on their shoulders instead of under a saddle: General Graardor Jr., Kree'arra Jr.,
K'ril Tsutsaroth Jr., Zilyana Jr., Noon and Midnight, Skotos, Lil' Bloat, the Dagannoth kings, Vet'ion Jr., Olmlet,
TzRek-Zuk and more. The kraken and Tangleroot give you a ride on top.

Your pet keeps its own tricks while you ride it. Right-click the mount for the pet's usual options: **Chase** on a
cat sends it after rats and **Dig** on a dog starts it digging, and your mount does it with you on its back. Dogs,
wolves and hounds also stop to dig now and then on their own while you stand still.

It's cosmetic and on your screen only, like the rest of the plugin.

## Mount Stable

Click the saddle icon in the RuneLite sidebar to open the **Mount Stable**:

- Shows the pet following you and whether it can be ridden (and why not, if it can't)
- A **Ride / Dismount** button
- Switches for the saddle and blanket, reins, natural riding motion, hiding your weapon and shield, and
  hiding your cape
- **Adjustments for this pet**: riding pose, size, seat height and seat forward/back. These are
  remembered for each pet, so every mount can be fitted exactly how you like it

There's also a small **on-screen mount button** (Alt-drag to move it, or turn it off in settings).

## Saddle and blanket

Every mount gets a leather saddle with stirrups over a blanket in your pet's colours. The
blanket is moulded to that pet's back, so it drapes over broad pets and fits snugly on thin ones.
Floating pets get just the blanket, as a rug to sit on cross-legged.

## Reins

You hold a pair of leather reins that run from your hands to the corners of your pet's mouth. They hang
with a little slack and follow both you and your pet's head every frame as it walks, runs and looks around.
Pets you sit on cross-legged (the floating ones) have no reins, since your hands rest in your lap.

## Natural riding motion

You ride with your mount rather than sitting stiffly on top of it:

- **Settling in:** after the poof you drop onto the saddle with a small bounce.
- **Stride sway:** a gentle side-to-side sway in time with your pet's walk or run.
- **Starting and stopping:** you rock back a little when your pet sets off and forward when it stops.
- **Smooth back:** the steps between your pet's animation frames are smoothed, so you ride the motion
  instead of jerking along with it.

Turn it off with **Natural riding motion** if you'd rather sit perfectly still.

## Everyone rides

Turn on **Everyone rides** (in the Mount Stable or under **Other players** in the settings) to see other
players riding the pets following them too, with the same mounts, saddles, reins and motion as yours.
Walk into the Grand Exchange and see everyone on their pets.

- It's on your screen only, like the rest of the plugin. Other players don't need Pet Mounts.
- Right-click a rider as usual to follow, trade with or report them. You can also **hold Shift** to see
  everyone normally.
- It's always off in the Wilderness and on PvP worlds.
- **Riders shown** sets how many riders are drawn at once, nearest first (10 by default).
- Players step off while they're skilling or fighting, just like you.

## Riding in style

- **Weapon, shield and cape are hidden while you ride**, so nothing pokes through your mount.
  On your screen only; they come back the moment you get off or hop off to fight.
- **Legs keep up with the ground**: pets without a run animation play their walk at running
  pace when you run, instead of sliding along.

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

- **Size:** each pet is enlarged until its back is about pony height, so your legs hang
  down naturally. Long, wide or tall pets are kept to about 2.5 tiles long and 2 tiles tall, and
  no pet is ever made smaller than it is. The **Mount size** setting scales them all.
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
| On-screen mount button | Show or hide the button |
| Mount size | Make every mount bigger or smaller |
| Riding pose | Automatic (the pose fitted to each pet), or choose one yourself |
| Seat height / Seat forward-back | Fine-tune where you sit (per-pet adjustments are in the Mount Stable) |
| Everyone rides | Show other players riding their pets too (hold Shift to see them normally) |
| Riders shown | The most other riders drawn at once |
| Saddle and blanket | Show the saddle and blanket |
| Reins | Hold reins running to your mount's mouth |
| Natural riding motion | Settle in, sway with the stride and rock when starting and stopping |
| Hide weapon and shield / Hide cape | Keep held items and capes from poking through the mount |

## Good to know

- This is purely visual and client-side. Other players see you walking with your pet as normal.
  Hiding your weapon and cape only changes what your own screen draws.
- The mount uses the pet's own idle, walk and run animations. On heavily enlarged pets,
  animations that move body parts (not just rotate them) can look slightly exaggerated.

## Running it locally

1. Open the folder in IntelliJ IDEA as a Gradle project.
2. Open `build.gradle` and click the green triangle next to the `run` task (or run `./gradlew run`).
3. RuneLite launches with Pet Mounts loaded. With a Jagex account, follow
   https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts to log in to the development client.

## How it works (for developers)

- The real pet and the real player model are hidden with a `RenderCallback`.
- Each rider on a pet is a `MountRig` (mount, saddle, reins, rider, pose and motion). Your own mount is one
  rig; **Everyone rides** (`OtherRiders.java`) keeps one per nearby player whose pet follows them, sharing
  the built mount models between riders of the same pet.
- The saddle is built by reshaping a spare game model into saddle geometry (`SaddleMesh.java`),
  moulded to the pet's back by probing the model in its idle pose.
- Weapon, shield and cape are hidden by changing the local appearance copy, like other appearance
  plugins on the Plugin Hub (`RiderLook.java`), and restored on dismount.
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
- The reins are rebuilt from a reshaped spare model whenever your hands or your pet's mouth move
  (`ReinMesh.java`). The mouth corners are found once on the pet's idle pose: the lowest part of the
  front tip of its head.
- The rider follows the seat through springs for settling, sway and surge (`RiderMotion.java`), timed
  to the mount's own animation cycle.
- The player's movement animations are overridden with the riding pose while mounted and
  restored on dismount.

## Credits

The seat-following technique and the seated riding poses are adapted from
[Rapid Mounts](https://github.com/RapidUrsa/RapidMounts) by RapidUrsa (BSD 2-Clause).
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Tip the developer

This plugin is free and always will be. If you enjoy it and want to say thanks, you can leave a tip on Cash App: [$VintageAdVenturesss](https://cash.app/$VintageAdVenturesss). Totally optional, and much appreciated.
