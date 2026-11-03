# Space Invaders

> Course: Software Development Practices (CSE2024) · 2026-2 · Section 24592

This IC-PBL course project extends and improves the Java [Space Invaders](https://en.wikipedia.org/wiki/Space_Invaders) clone from [RobertoIA/Invaders](https://github.com/RobertoIA/Invaders). Students apply software engineering practices through requirements engineering, team development, code review, and pull-request-based integration into a shared codebase.

## Screenshots
| Title Screen | Game Screen | Score Screen |
| :---: | :---: | :---: |
| ![Space Invaders title screen](https://user-images.githubusercontent.com/69495129/136980139-7ad6adab-3f11-4711-b0a6-341080aa3361.png) | ![Space Invaders game screen](https://user-images.githubusercontent.com/69495129/136980236-c5d9ef85-f09a-47a7-b9d9-948f7b624002.png) | ![Space Invaders score screen](https://user-images.githubusercontent.com/69495129/136980681-93dcadaf-08cb-48d8-90c9-68c651a115c9.png) |

## Development
- Recommended IDE: IntelliJ IDEA
- Runtime requirement: Java 17 or later. Development JDKs: 17, 21, or 25; keep the language level at Java 17.
- Before implementing a feature, build and run the baseline game and analyze the relevant source code.

To run the baseline in IntelliJ IDEA, mark `src` as **Sources Root** and `res` as **Resources Root**, then run `engine.Core`. The resource directory must be on the runtime classpath: the game loads `graphics`, `scores`, and `font.ttf` from its root. Installing the font in your operating system does not make it available to the game.

## Attribution and Licensing

This course project is based on [RobertoIA/Invaders](https://github.com/RobertoIA/Invaders). The upstream repository does not currently declare an open-source license. This repository therefore does not assert a new open-source license over the upstream code or third-party assets. Any permission or licensing terms confirmed by the relevant rights holders should be documented here before redistribution beyond the permissions provided through GitHub.

The bundled `res/font.ttf` is **Space Invaders** by [kylemaoin](https://fontstruct.com/fontstructions/show/282751/space_invaders_9) (© 2010), shared under [Creative Commons Attribution-ShareAlike 3.0](https://creativecommons.org/licenses/by-sa/3.0/). It came from the [official FontStruct download](https://fontstruct.com/fontstructions/download/282751); its font data is unchanged, and its filename was changed from `space-invaders.ttf` to `font.ttf` to match the game's resource lookup. The accompanying [license](res/font-license/license.txt) and [readme](res/font-license/readme.txt) are included as required by that download. This license applies to the font, not to the upstream game code or other assets. Verify those assets' licenses separately before redistributing them.
