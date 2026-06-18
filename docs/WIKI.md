# 🌾 SeriaFarm Wiki

SeriaFarm is a high-performance farming system designed for large-scale RPG environments. It includes crop regeneration, custom growth speeds, and Farming Fortune integration.

## 🛠 Commands
| Command | Alias | Permission | Description |
|---------|-------|------------|-------------|
| `/sfarm editor` | - | `seriafarm.admin` (Op) | Open Admin Panel |
| `/sfarm wand` | - | `seriafarm.admin` (Op) | Get Selection Wand |
| `/sfarm pos1` | - | `seriafarm.admin` (Op) | Set Position 1 |
| `/sfarm pos2` | - | `seriafarm.admin` (Op) | Set Position 2 |
| `/sfarm create <name>` | - | `seriafarm.admin` (Op) | Create Region |
| `/sfarm soil <key> <player> [amount]` | - | `seriafarm.admin` (Op) | Give soil item |
| `/sfarm seed <key> <player> [amount]` | - | `seriafarm.admin` (Op) | Give seed item |
| `/sfarm clear` | - | `seriafarm.admin` (Op) | Clear Selection and Preview Particles |
| `/sfarm reload` | - | `seriafarm.admin` (Op) | Reload configuration |

## 📊 Placeholders
- `%seriafarm_farming_fortune%`: Current total Farming Fortune.
- `%seriafarm_crops_harvested%`: Total crops harvested by player.

## ⚙️ Configuration Example

### config.yml
```yaml
settings:
  regeneration:
    enabled: true
    default_delay: 300 # seconds
  growth:
    speed_multiplier: 1.0

crops:
  wheat:
    regen_time: 60
    xp_gain: 5.0
    fortune_multiplier: 1.0
```
